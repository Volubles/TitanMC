package com.voluble.titanMC.regions.admin;

import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.regions.model.RegionGeometry;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.model.RegionTextFlag;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.RegionSubject;
import com.voluble.titanMC.regions.service.RegionEngine;
import com.voluble.titanMC.regions.service.RegionMutationResult;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RegionAdminService {

	public static final String NAMESPACE = "custom";
	public static final int DEFAULT_PRIORITY = 100;

	private final RegionEngine regions;

	public RegionAdminService(RegionEngine regions) {
		this.regions = Objects.requireNonNull(regions, "regions");
	}

	public RegionMutationResult create(
		String name,
		WorldId worldId,
		int priority,
		RegionGeometry geometry
	) {
		return regions.create(key(name), worldId, priority, geometry).join();
	}

	public RegionMutationResult redefine(
		String name,
		WorldId worldId,
		RegionGeometry geometry
	) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.update(
			existing.id(), existing.revision(), existing.key(), worldId, existing.priority(), geometry
		).join();
	}

	public RegionMutationResult delete(String name, WorldId worldId) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.delete(existing.id(), existing.revision()).join();
	}

	public RegionMutationResult setFlag(
		String name,
		WorldId worldId,
		ProtectionAction action,
		ProtectionDecision decision
	) {
		return setFlag(name, worldId, action, RegionSubject.EVERYONE, decision);
	}

	public RegionMutationResult setFlag(
		String name,
		WorldId worldId,
		ProtectionAction action,
		RegionSubject subject,
		ProtectionDecision decision
	) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.setFlags(
			existing.id(), existing.revision(), existing.flags().with(action, subject, decision)
		).join();
	}

	public RegionMutationResult addOwner(String name, WorldId worldId, UUID playerId) {
		return updateAccess(name, worldId, access -> access.withOwner(playerId));
	}

	public RegionMutationResult removeOwner(String name, WorldId worldId, UUID playerId) {
		return updateAccess(name, worldId, access -> access.withoutOwner(playerId));
	}

	public RegionMutationResult addMember(String name, WorldId worldId, UUID playerId) {
		return updateAccess(name, worldId, access -> access.withMember(playerId));
	}

	public RegionMutationResult removeMember(String name, WorldId worldId, UUID playerId) {
		return updateAccess(name, worldId, access -> access.withoutMember(playerId));
	}

	public RegionMutationResult replaceAccess(
		String name,
		WorldId worldId,
		RegionAccessSet access
	) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.setAccess(existing.id(), existing.revision(), access).join();
	}

	public RegionMutationResult setPriority(String name, WorldId worldId, int priority) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.update(
			existing.id(),
			existing.revision(),
			existing.key(),
			existing.worldId(),
			priority,
			existing.geometry()
		).join();
	}

	public RegionMutationResult setText(
		String name,
		WorldId worldId,
		RegionTextFlag flag,
		String value
	) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.setText(
			existing.id(), existing.revision(), existing.text().with(flag, value)
		).join();
	}

	public RegionDefinition find(WorldId worldId, String name) {
		return regions.find(worldId, key(name));
	}

	public List<RegionDefinition> list(WorldId worldId) {
		return regions.snapshot().definitions().stream()
			.filter(region -> region.worldId().equals(worldId))
			.filter(region -> region.key().namespace().equals(NAMESPACE))
			.sorted(Comparator.comparing(RegionDefinition::key))
			.toList();
	}

	public List<String> names() {
		return regions.snapshot().definitions().stream()
			.filter(region -> region.key().namespace().equals(NAMESPACE))
			.map(region -> region.key().name())
			.distinct()
			.sorted()
			.toList();
	}

	private RegionMutationResult updateAccess(
		String name,
		WorldId worldId,
		java.util.function.UnaryOperator<RegionAccessSet> update
	) {
		RegionDefinition existing = find(worldId, name);
		if (existing == null) return notFound(name);
		return regions.setAccess(
			existing.id(),
			existing.revision(),
			Objects.requireNonNull(update.apply(existing.access()), "updated access")
		).join();
	}

	private static RegionKey key(String name) {
		return RegionKey.of(NAMESPACE, name);
	}

	private static RegionMutationResult.Failure notFound(String name) {
		return new RegionMutationResult.Failure(
			RegionMutationResult.Reason.NOT_FOUND, "Unknown region: " + name
		);
	}
}
