package com.voluble.titanMC.regions.protection.service;

import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class RegionGroupMembership {

	private final RegionGroupProvider provider;
	private final ProtectionActor actor;
	private final Map<GroupKey, Boolean> matches = new HashMap<>();

	RegionGroupMembership(RegionGroupProvider provider, ProtectionActor actor) {
		this.provider = Objects.requireNonNull(provider, "provider");
		this.actor = Objects.requireNonNull(actor, "actor");
	}

	boolean matches(WorldId worldId, String group) {
		GroupKey key = new GroupKey(
			Objects.requireNonNull(worldId, "worldId"),
			Objects.requireNonNull(group, "group").toLowerCase(Locale.ROOT)
		);
		return matches.computeIfAbsent(key, ignored -> provider.isInGroup(actor, worldId, key.group()));
	}

	private record GroupKey(WorldId worldId, String group) {}
}
