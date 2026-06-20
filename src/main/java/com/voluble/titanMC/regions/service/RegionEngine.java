package com.voluble.titanMC.regions.service;

import com.voluble.titanMC.regions.index.RegionIndex;
import com.voluble.titanMC.regions.index.RegionIndexBuildException;
import com.voluble.titanMC.regions.index.RegionIndexOptions;
import com.voluble.titanMC.regions.index.RegionIndexSnapshot;
import com.voluble.titanMC.regions.index.RegionReadView;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionGeometry;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.persistence.RegionRepository;
import com.voluble.titanMC.regions.persistence.RegionStorageException;
import com.voluble.titanMC.regions.persistence.SqliteRegionRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionEngine implements AutoCloseable {

	private final RegionRepository repository;
	private final RegionIndexOptions options;
	private final RegionIndex index;
	private final ThreadPoolExecutor writer;
	private final RegionRuntimeOptions runtimeOptions;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicReference<RegionEngineHealth> health = new AtomicReference<>(RegionEngineHealth.HEALTHY);
	private final AtomicLong lastPublishedAtEpochMillis = new AtomicLong();

	private RegionEngine(RegionRepository repository, RegionRuntimeOptions runtimeOptions, RegionIndex index) {
		this.repository = repository;
		this.runtimeOptions = runtimeOptions;
		this.options = runtimeOptions.indexOptions();
		this.index = index;
		this.writer = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(runtimeOptions.mutationQueueCapacity()),
			Thread.ofPlatform().name("titan-region-writer").factory(),
			new ThreadPoolExecutor.AbortPolicy()
		);
	}

	public static RegionEngine open(Path databasePath) throws RegionStorageException {
		return open(new SqliteRegionRepository(databasePath), RegionRuntimeOptions.defaults());
	}

	public static RegionEngine open(RegionRepository repository, RegionIndexOptions options) throws RegionStorageException {
		return open(repository, new RegionRuntimeOptions(options, 1_024, java.time.Duration.ofSeconds(10)));
	}

	public static RegionEngine open(RegionRepository repository, RegionRuntimeOptions runtimeOptions) throws RegionStorageException {
		Objects.requireNonNull(repository, "repository");
		Objects.requireNonNull(runtimeOptions, "runtimeOptions");
		try {
			repository.initialize();
			List<RegionDefinition> definitions = repository.loadAll();
			RegionIndex index = new RegionIndex();
			RegionIndexSnapshot initial = RegionIndexSnapshot.build(1L, definitions, runtimeOptions.indexOptions());
			if (!index.publish(index.snapshot(), initial)) {
				throw new IllegalStateException("Failed to publish initial region snapshot");
			}
			return new RegionEngine(repository, runtimeOptions, index);
		} catch (SQLException | RegionIndexBuildException | RuntimeException exception) {
			try {
				repository.close();
			} catch (Exception suppressed) {
				exception.addSuppressed(suppressed);
			}
			throw new RegionStorageException("Failed to initialize Titan Region Engine", exception);
		}
	}

	public RegionIndexSnapshot snapshot() {
		return index.snapshot();
	}

	public RegionReadView readView() {
		return index.readView();
	}

	public RegionEngineHealth health() {
		return health.get();
	}

	public RegionEngineStats stats() {
		RegionIndexSnapshot snapshot = index.snapshot();
		return new RegionEngineStats(
			health.get(), snapshot.version(), snapshot.definitions().size(), writer.getQueue().size(),
			runtimeOptions.mutationQueueCapacity(), lastPublishedAtEpochMillis.get()
		);
	}

	public RegionDefinition find(RegionId id) {
		return index.find(id);
	}

	public RegionDefinition find(WorldId worldId, RegionKey key) {
		return index.find(worldId, key);
	}

	public List<RegionDefinition> findAll(WorldId worldId, int x, int y, int z) {
		return index.findAll(worldId, x, y, z);
	}

	public List<RegionDefinition> findIntersecting(WorldId worldId, BlockBox box) {
		return index.findIntersecting(worldId, box);
	}

	public CompletableFuture<RegionMutationResult> create(
		RegionKey key,
		WorldId worldId,
		int priority,
		RegionGeometry geometry
	) {
		RegionDefinition definition;
		try {
			definition = RegionDefinition.create(key, worldId, priority, geometry);
		} catch (RuntimeException exception) {
			return CompletableFuture.completedFuture(new RegionMutationResult.Failure(
				RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage()
			));
		}
		return submit(() -> saveMutation(definition, true));
	}

	public CompletableFuture<RegionMutationResult> update(
		RegionId id,
		long expectedRevision,
		RegionKey key,
		WorldId worldId,
		int priority,
		RegionGeometry geometry
	) {
		return submit(() -> {
			RegionDefinition existing = index.find(id);
			if (existing == null) return failure(RegionMutationResult.Reason.NOT_FOUND, "Region does not exist: " + id);
			if (existing.revision() != expectedRevision) {
				return failure(RegionMutationResult.Reason.STALE_REVISION,
					"Expected revision " + expectedRevision + " but found " + existing.revision());
			}
			RegionDefinition updated;
			try {
				updated = new RegionDefinition(
					id, key, worldId, priority, geometry, existing.createdAt(), Instant.now(), existing.revision() + 1L
				);
			} catch (RuntimeException exception) {
				return failure(RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage());
			}
			return saveMutation(updated, false);
		});
	}

	public CompletableFuture<RegionMutationResult> delete(RegionId id, long expectedRevision) {
		return submit(() -> {
			RegionIndexSnapshot active = index.snapshot();
			RegionDefinition existing = active.find(id);
			if (existing == null) return failure(RegionMutationResult.Reason.NOT_FOUND, "Region does not exist: " + id);
			if (existing.revision() != expectedRevision) {
				return failure(RegionMutationResult.Reason.STALE_REVISION,
					"Expected revision " + expectedRevision + " but found " + existing.revision());
			}
			Map<RegionId, RegionDefinition> definitions = mutableDefinitions(active);
			definitions.remove(id);
			RegionIndexSnapshot replacement;
			try {
				replacement = RegionIndexSnapshot.build(active.version() + 1L, definitions.values(), options);
			} catch (RegionIndexBuildException exception) {
				return failure(RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage());
			}
		try {
			repository.delete(id);
		} catch (SQLException exception) {
			markFailed();
			return failure(RegionMutationResult.Reason.STORAGE_FAILURE, exception.getMessage());
		}
		if (!index.publish(active, replacement)) {
			markFailed();
			return failure(RegionMutationResult.Reason.INTERNAL_CONFLICT, "Snapshot changed outside the mutation writer");
		}
		markPublished();
			return new RegionMutationResult.Success(existing);
		});
	}

	public CompletableFuture<RegionBatchResult> submit(RegionMutationBatch batch) {
		Objects.requireNonNull(batch, "batch");
		return submitBatch(() -> applyBatch(batch));
	}

	private RegionBatchResult applyBatch(RegionMutationBatch batch) {
		RegionIndexSnapshot active = index.snapshot();
		Map<RegionId, RegionDefinition> definitions = mutableDefinitions(active);
		Map<RegionId, RegionDefinition> saves = new LinkedHashMap<>();
		Map<RegionId, RegionDefinition> deletes = new LinkedHashMap<>();
		Instant timestamp = Instant.now();

		for (int operationIndex = 0; operationIndex < batch.operations().size(); operationIndex++) {
			RegionMutationBatch.Operation operation = batch.operations().get(operationIndex);
			if (operation instanceof RegionMutationBatch.Create create) {
				RegionDefinition definition = create.definition();
				if (definitions.containsKey(definition.id())) {
					return batchFailure(operationIndex, RegionMutationResult.Reason.DUPLICATE_KEY, "Region id already exists: " + definition.id());
				}
				definitions.put(definition.id(), definition);
				saves.put(definition.id(), definition);
				deletes.remove(definition.id());
			} else if (operation instanceof RegionMutationBatch.Update update) {
				RegionDefinition existing = definitions.get(update.id());
				if (existing == null) return batchFailure(operationIndex, RegionMutationResult.Reason.NOT_FOUND, "Region does not exist: " + update.id());
				if (existing.revision() != update.expectedRevision()) {
					return batchFailure(operationIndex, RegionMutationResult.Reason.STALE_REVISION,
						"Expected revision " + update.expectedRevision() + " but found " + existing.revision());
				}
				RegionDefinition updated;
				try {
					updated = new RegionDefinition(
						existing.id(), update.key(), update.worldId(), update.priority(), update.geometry(),
						existing.createdAt(), timestamp, existing.revision() + 1L
					);
				} catch (RuntimeException exception) {
					return batchFailure(operationIndex, RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage());
				}
				definitions.put(updated.id(), updated);
				saves.put(updated.id(), updated);
				deletes.remove(updated.id());
			} else if (operation instanceof RegionMutationBatch.Delete delete) {
				RegionDefinition existing = definitions.get(delete.id());
				if (existing == null) return batchFailure(operationIndex, RegionMutationResult.Reason.NOT_FOUND, "Region does not exist: " + delete.id());
				if (existing.revision() != delete.expectedRevision()) {
					return batchFailure(operationIndex, RegionMutationResult.Reason.STALE_REVISION,
						"Expected revision " + delete.expectedRevision() + " but found " + existing.revision());
				}
				definitions.remove(existing.id());
				saves.remove(existing.id());
				deletes.put(existing.id(), existing);
			}
		}

		RegionIndexSnapshot replacement;
		try {
			replacement = RegionIndexSnapshot.build(active.version() + 1L, definitions.values(), options);
		} catch (RegionIndexBuildException exception) {
			return batchFailure(batch.operations().size() - 1, RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage());
		}
		try {
			repository.applyBatch(List.copyOf(saves.values()), List.copyOf(deletes.keySet()));
		} catch (SQLException exception) {
			markFailed();
			return batchFailure(-1, RegionMutationResult.Reason.STORAGE_FAILURE, exception.getMessage());
		}
		if (!index.publish(active, replacement)) {
			markFailed();
			return batchFailure(-1, RegionMutationResult.Reason.INTERNAL_CONFLICT, "Snapshot changed outside the mutation writer");
		}
		markPublished();
		return new RegionBatchResult.Success(replacement.version(), List.copyOf(saves.values()), List.copyOf(deletes.values()));
	}

	private CompletableFuture<RegionBatchResult> submitBatch(BatchMutation mutation) {
		if (closed.get()) return CompletableFuture.completedFuture(batchFailure(-1, RegionMutationResult.Reason.ENGINE_CLOSED, "Region engine is closed"));
		if (!health.get().acceptsMutations()) return CompletableFuture.completedFuture(batchFailure(-1, RegionMutationResult.Reason.ENGINE_UNHEALTHY, "Region engine is not healthy"));
		try {
			return CompletableFuture.supplyAsync(() -> {
				if (!health.get().acceptsMutations()) return batchFailure(-1, RegionMutationResult.Reason.ENGINE_UNHEALTHY, "Region engine is not healthy");
				try {
					return mutation.run();
				} catch (RuntimeException exception) {
					markFailed();
					return batchFailure(-1, RegionMutationResult.Reason.INTERNAL_CONFLICT, exception.getMessage());
				}
			}, writer);
		} catch (RejectedExecutionException exception) {
			RegionMutationResult.Reason reason = closed.get()
				? RegionMutationResult.Reason.ENGINE_CLOSED
				: RegionMutationResult.Reason.QUEUE_FULL;
			return CompletableFuture.completedFuture(batchFailure(-1, reason, reason.name()));
		}
	}

	private static RegionBatchResult.Failure batchFailure(int operationIndex, RegionMutationResult.Reason reason, String message) {
		return new RegionBatchResult.Failure(operationIndex, reason, message == null ? reason.name() : message);
	}

	private RegionMutationResult saveMutation(RegionDefinition definition, boolean createOnly) {
		RegionIndexSnapshot active = index.snapshot();
		RegionDefinition sameKey = active.find(definition.worldId(), definition.key());
		if (sameKey != null && (!sameKey.id().equals(definition.id()) || createOnly)) {
			return failure(RegionMutationResult.Reason.DUPLICATE_KEY, "Region key already exists: " + definition.key());
		}
		Map<RegionId, RegionDefinition> definitions = mutableDefinitions(active);
		definitions.put(definition.id(), definition);
		RegionIndexSnapshot replacement;
		try {
			replacement = RegionIndexSnapshot.build(active.version() + 1L, definitions.values(), options);
		} catch (RegionIndexBuildException exception) {
			return failure(RegionMutationResult.Reason.INVALID_GEOMETRY, exception.getMessage());
		}
		try {
			repository.save(definition);
		} catch (SQLException exception) {
			markFailed();
			return failure(RegionMutationResult.Reason.STORAGE_FAILURE, exception.getMessage());
		}
		if (!index.publish(active, replacement)) {
			markFailed();
			return failure(RegionMutationResult.Reason.INTERNAL_CONFLICT, "Snapshot changed outside the mutation writer");
		}
		markPublished();
		return new RegionMutationResult.Success(definition);
	}

	private CompletableFuture<RegionMutationResult> submit(Mutation mutation) {
		if (closed.get()) return CompletableFuture.completedFuture(failure(RegionMutationResult.Reason.ENGINE_CLOSED, "Region engine is closed"));
		if (!health.get().acceptsMutations()) return CompletableFuture.completedFuture(failure(RegionMutationResult.Reason.ENGINE_UNHEALTHY, "Region engine is not healthy"));
		try {
			return CompletableFuture.supplyAsync(() -> {
				if (!health.get().acceptsMutations()) return failure(RegionMutationResult.Reason.ENGINE_UNHEALTHY, "Region engine is not healthy");
				try {
					return mutation.run();
				} catch (RuntimeException exception) {
					markFailed();
					return failure(RegionMutationResult.Reason.INTERNAL_CONFLICT, exception.getMessage());
				}
			}, writer);
		} catch (RejectedExecutionException exception) {
			RegionMutationResult.Reason reason = closed.get()
				? RegionMutationResult.Reason.ENGINE_CLOSED
				: RegionMutationResult.Reason.QUEUE_FULL;
			return CompletableFuture.completedFuture(failure(reason, reason.name()));
		}
	}

	private void markFailed() {
		health.compareAndSet(RegionEngineHealth.HEALTHY, RegionEngineHealth.FAILED);
	}

	private void markPublished() {
		lastPublishedAtEpochMillis.set(System.currentTimeMillis());
	}

	private static Map<RegionId, RegionDefinition> mutableDefinitions(RegionIndexSnapshot snapshot) {
		Map<RegionId, RegionDefinition> definitions = new LinkedHashMap<>();
		for (RegionDefinition definition : snapshot.definitions()) definitions.put(definition.id(), definition);
		return definitions;
	}

	private static RegionMutationResult.Failure failure(RegionMutationResult.Reason reason, String message) {
		return new RegionMutationResult.Failure(reason, message == null ? reason.name() : message);
	}

	@Override
	public void close() throws RegionStorageException {
		if (!closed.compareAndSet(false, true)) return;
		writer.shutdown();
		try {
			if (!writer.awaitTermination(runtimeOptions.closeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
				writer.shutdownNow();
				if (!writer.awaitTermination(runtimeOptions.closeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
					throw new RegionStorageException("Region writer did not terminate", null);
				}
			}
			repository.close();
			health.set(RegionEngineHealth.CLOSED);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			writer.shutdownNow();
			throw new RegionStorageException("Interrupted while closing region engine", exception);
		} catch (SQLException exception) {
			throw new RegionStorageException("Failed to close region repository", exception);
		}
	}

	@FunctionalInterface
	private interface Mutation {
		RegionMutationResult run();
	}

	@FunctionalInterface
	private interface BatchMutation {
		RegionBatchResult run();
	}
}
