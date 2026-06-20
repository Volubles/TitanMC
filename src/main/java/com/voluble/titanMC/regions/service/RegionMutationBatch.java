package com.voluble.titanMC.regions.service;

import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionGeometry;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RegionMutationBatch(List<Operation> operations) {

	public RegionMutationBatch {
		operations = List.copyOf(operations);
		if (operations.isEmpty()) throw new IllegalArgumentException("mutation batch must not be empty");
		if (operations.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("operations must not contain null");
	}

	public static Builder builder() {
		return new Builder();
	}

	public sealed interface Operation permits Create, Update, Delete {}

	public record Create(RegionDefinition definition) implements Operation {
		public Create {
			Objects.requireNonNull(definition, "definition");
		}
	}

	public record Update(
		RegionId id,
		long expectedRevision,
		RegionKey key,
		WorldId worldId,
		int priority,
		RegionGeometry geometry
	) implements Operation {
		public Update {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(worldId, "worldId");
			Objects.requireNonNull(geometry, "geometry");
			if (expectedRevision < 1L) throw new IllegalArgumentException("expectedRevision must be positive");
		}
	}

	public record Delete(RegionId id, long expectedRevision) implements Operation {
		public Delete {
			Objects.requireNonNull(id, "id");
			if (expectedRevision < 1L) throw new IllegalArgumentException("expectedRevision must be positive");
		}
	}

	public static final class Builder {
		private final List<Operation> operations = new ArrayList<>();

		public Builder create(RegionDefinition definition) {
			operations.add(new Create(definition));
			return this;
		}

		public Builder update(
			RegionId id,
			long expectedRevision,
			RegionKey key,
			WorldId worldId,
			int priority,
			RegionGeometry geometry
		) {
			operations.add(new Update(id, expectedRevision, key, worldId, priority, geometry));
			return this;
		}

		public Builder delete(RegionId id, long expectedRevision) {
			operations.add(new Delete(id, expectedRevision));
			return this;
		}

		public RegionMutationBatch build() {
			return new RegionMutationBatch(operations);
		}
	}
}
