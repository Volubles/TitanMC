package com.voluble.titanMC.regions.protection.policy;

import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;

import java.util.List;

public interface RegionGroupProvider {

	boolean isInGroup(ProtectionActor actor, WorldId worldId, String group);

	default List<String> groups() {
		return List.of();
	}

	static RegionGroupProvider none() {
		return (actor, worldId, group) -> false;
	}
}
