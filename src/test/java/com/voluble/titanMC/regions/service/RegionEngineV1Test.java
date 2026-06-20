package com.voluble.titanMC.regions.service;

import com.voluble.titanMC.regions.index.RegionIndexOptions;
import com.voluble.titanMC.regions.index.RegionReadView;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.persistence.RegionRepository;
import com.voluble.titanMC.regions.persistence.RegionStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEngineV1Test {

	@TempDir
	Path temporaryDirectory;

	@Test
	void staleRevisionCannotOverwriteOrDeleteNewerState() throws Exception {
		WorldId world = world();
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("revision.db"))) {
			RegionDefinition created = success(engine.create(key("alpha"), world, 1, geometry(0)).join());
			RegionDefinition updated = success(engine.update(
				created.id(), created.revision(), key("alpha"), world, 2, geometry(16)
			).join());

			RegionMutationResult.Failure staleUpdate = failure(engine.update(
				created.id(), created.revision(), key("alpha"), world, 3, geometry(32)
			).join());
			RegionMutationResult.Failure staleDelete = failure(engine.delete(created.id(), created.revision()).join());

			assertEquals(RegionMutationResult.Reason.STALE_REVISION, staleUpdate.reason());
			assertEquals(RegionMutationResult.Reason.STALE_REVISION, staleDelete.reason());
			assertEquals(updated, engine.find(created.id()));
			assertEquals(RegionEngineHealth.HEALTHY, engine.health());
		}
	}

	@Test
	void invalidBatchRollsBackEveryEarlierOperation() throws Exception {
		Path database = temporaryDirectory.resolve("rollback.db");
		WorldId world = world();
		RegionId rejectedId;
		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition existing = success(engine.create(key("existing"), world, 0, geometry(0)).join());
			RegionDefinition rejected = RegionDefinition.create(key("rejected"), world, 0, geometry(32));
			rejectedId = rejected.id();
			RegionMutationBatch batch = RegionMutationBatch.builder()
				.create(rejected)
				.delete(existing.id(), existing.revision() + 1L)
				.build();

			RegionBatchResult.Failure result = assertInstanceOf(RegionBatchResult.Failure.class, engine.submit(batch).join());
			assertEquals(1, result.operationIndex());
			assertEquals(RegionMutationResult.Reason.STALE_REVISION, result.reason());
			assertNull(engine.find(rejected.id()));
			assertEquals(existing, engine.find(existing.id()));
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertNull(reloaded.find(rejectedId));
			assertEquals(1, reloaded.snapshot().definitions().size());
		}
	}

	@Test
	void atomicBatchCanSwapTwoUniqueRegionKeys() throws Exception {
		Path database = temporaryDirectory.resolve("swap.db");
		WorldId world = world();
		RegionId firstId;
		RegionId secondId;
		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition first = success(engine.create(key("first"), world, 0, geometry(0)).join());
			RegionDefinition second = success(engine.create(key("second"), world, 0, geometry(32)).join());
			firstId = first.id();
			secondId = second.id();
			RegionMutationBatch swap = RegionMutationBatch.builder()
				.update(first.id(), first.revision(), key("second"), world, 0, geometry(0))
				.update(second.id(), second.revision(), key("first"), world, 0, geometry(32))
				.build();

			assertInstanceOf(RegionBatchResult.Success.class, engine.submit(swap).join());
			assertEquals(first.id(), engine.find(world, key("second")).id());
			assertEquals(second.id(), engine.find(world, key("first")).id());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(firstId, reloaded.find(world, key("second")).id());
			assertEquals(secondId, reloaded.find(world, key("first")).id());
		}
	}

	@Test
	void readViewRemainsPinnedAfterNewSnapshotsArePublished() throws Exception {
		WorldId world = world();
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("view.db"))) {
			RegionDefinition first = success(engine.create(key("first"), world, 0, geometry(0)).join());
			RegionReadView pinned = engine.readView();
			long pinnedVersion = pinned.version();
			RegionDefinition second = success(engine.create(key("second"), world, 0, geometry(32)).join());

			assertEquals(first, pinned.find(first.id()));
			assertNull(pinned.find(second.id()));
			assertEquals(pinnedVersion, pinned.version());
			assertNotEquals(pinnedVersion, engine.readView().version());
			assertEquals(second, engine.readView().find(second.id()));
		}
	}

	@Test
	void boundedWriterRejectsWorkWhenQueueIsFull() throws Exception {
		BlockingRepository repository = new BlockingRepository();
		RegionRuntimeOptions options = new RegionRuntimeOptions(RegionIndexOptions.defaults(), 1, Duration.ofSeconds(2));
		WorldId world = world();
		try (RegionEngine engine = RegionEngine.open(repository, options)) {
			var running = engine.create(key("running"), world, 0, geometry(0));
			assertTrue(repository.saveEntered.await(2, TimeUnit.SECONDS));
			var queued = engine.create(key("queued"), world, 0, geometry(32));
			RegionMutationResult.Failure rejected = failure(engine.create(key("rejected"), world, 0, geometry(64)).join());

			assertEquals(RegionMutationResult.Reason.QUEUE_FULL, rejected.reason());
			assertEquals(1, engine.stats().queuedMutations());
			repository.releaseSave.countDown();
			assertTrue(running.join().successful());
			assertTrue(queued.join().successful());
		}
	}

	@Test
	void storageAndUnexpectedRepositoryFailuresMakeEngineFailClosed() throws Exception {
		FailingRepository sqlFailure = new FailingRepository(new SQLException("disk unavailable"));
		try (RegionEngine engine = RegionEngine.open(sqlFailure, RegionIndexOptions.defaults())) {
			RegionMutationResult.Failure first = failure(engine.create(key("first"), world(), 0, geometry(0)).join());
			RegionMutationResult.Failure second = failure(engine.create(key("second"), world(), 0, geometry(32)).join());
			assertEquals(RegionMutationResult.Reason.STORAGE_FAILURE, first.reason());
			assertEquals(RegionEngineHealth.FAILED, engine.health());
			assertEquals(RegionMutationResult.Reason.ENGINE_UNHEALTHY, second.reason());
		}

		FailingRepository runtimeFailure = new FailingRepository(new IllegalStateException("repository bug"));
		try (RegionEngine engine = RegionEngine.open(runtimeFailure, RegionIndexOptions.defaults())) {
			RegionMutationResult.Failure result = failure(engine.create(key("first"), world(), 0, geometry(0)).join());
			assertEquals(RegionMutationResult.Reason.INTERNAL_CONFLICT, result.reason());
			assertEquals(RegionEngineHealth.FAILED, engine.health());
		}
	}

	@Test
	void legacyDatabaseSchemaIsRejectedWithoutMigration() throws Exception {
		Path database = temporaryDirectory.resolve("legacy.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			statement.execute("PRAGMA user_version = 1");
		}

		RegionStorageException exception = assertThrows(RegionStorageException.class, () -> RegionEngine.open(database));
		assertTrue(exception.getCause().getMessage().contains("Unsupported region database schema 1"));
	}

	@Test
	void extremePrioritiesRetainDeterministicOrder() throws Exception {
		WorldId world = world();
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("priorities.db"))) {
			RegionDefinition lowest = success(engine.create(key("lowest"), world, Integer.MIN_VALUE, geometry(0)).join());
			RegionDefinition highest = success(engine.create(key("highest"), world, Integer.MAX_VALUE, geometry(0)).join());
			assertEquals(List.of(highest, lowest), engine.findAll(world, 1, 1, 1));
		}
	}

	private static RegionDefinition success(RegionMutationResult result) {
		return assertInstanceOf(RegionMutationResult.Success.class, result).region();
	}

	private static RegionMutationResult.Failure failure(RegionMutationResult result) {
		return assertInstanceOf(RegionMutationResult.Failure.class, result);
	}

	private static WorldId world() {
		return new WorldId(UUID.randomUUID());
	}

	private static RegionKey key(String name) {
		return RegionKey.of("cell", name);
	}

	private static BlockBox box(int x) {
		return new BlockBox(x, 0, 0, x + 16, 16, 16);
	}

	private static CuboidGeometry geometry(int x) {
		return new CuboidGeometry(box(x));
	}

	private static class MemoryRepository implements RegionRepository {
		final List<RegionDefinition> definitions = new ArrayList<>();

		@Override public void initialize() {}
		@Override public synchronized List<RegionDefinition> loadAll() { return List.copyOf(definitions); }
		@Override public synchronized void save(RegionDefinition definition) throws SQLException {
			definitions.removeIf(existing -> existing.id().equals(definition.id()));
			definitions.add(definition);
		}
		@Override public synchronized void delete(RegionId id) { definitions.removeIf(region -> region.id().equals(id)); }
		@Override public synchronized void applyBatch(List<RegionDefinition> saves, List<RegionId> deletes) {
			definitions.removeIf(region -> deletes.contains(region.id()) || saves.stream().anyMatch(saved -> saved.id().equals(region.id())));
			definitions.addAll(saves);
		}
		@Override public void close() {}
	}

	private static final class BlockingRepository extends MemoryRepository {
		final CountDownLatch saveEntered = new CountDownLatch(1);
		final CountDownLatch releaseSave = new CountDownLatch(1);
		final AtomicBoolean blockNextSave = new AtomicBoolean(true);

		@Override
		public synchronized void save(RegionDefinition definition) throws SQLException {
			if (blockNextSave.compareAndSet(true, false)) {
				saveEntered.countDown();
				try {
					if (!releaseSave.await(2, TimeUnit.SECONDS)) throw new SQLException("test save timed out");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new SQLException("test interrupted", exception);
				}
			}
			super.save(definition);
		}
	}

	private static final class FailingRepository extends MemoryRepository {
		private final Exception failure;

		private FailingRepository(Exception failure) {
			this.failure = failure;
		}

		@Override
		public synchronized void save(RegionDefinition definition) throws SQLException {
			if (failure instanceof SQLException sqlException) throw sqlException;
			if (failure instanceof RuntimeException runtimeException) throw runtimeException;
			throw new SQLException(failure);
		}
	}
}
