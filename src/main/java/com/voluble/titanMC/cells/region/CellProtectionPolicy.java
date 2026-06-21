package com.voluble.titanMC.cells.region;

import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.policy.RegionProtectionPolicy;

import java.util.EnumSet;
import java.util.Set;

public final class CellProtectionPolicy implements RegionProtectionPolicy {
	public static final String NAMESPACE = "cell";
	private static final Set<ProtectionAction> MEMBER_ACTIONS = EnumSet.of(
		ProtectionAction.BLOCK_BREAK, ProtectionAction.BLOCK_PLACE,
		ProtectionAction.BLOCK_INTERACT, ProtectionAction.PHYSICAL_INTERACT,
		ProtectionAction.CONTAINER_OPEN, ProtectionAction.ENTITY_PLACE,
		ProtectionAction.ENTITY_INTERACT, ProtectionAction.ENTITY_DAMAGE,
		ProtectionAction.HANGING_MODIFY, ProtectionAction.BUCKET_FILL,
		ProtectionAction.BUCKET_EMPTY, ProtectionAction.VEHICLE_PLACE,
		ProtectionAction.VEHICLE_ENTER, ProtectionAction.VEHICLE_MODIFY
	);

	@Override public String id() { return "cell-default"; }
	@Override public String namespace() { return NAMESPACE; }

	@Override
	public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) {
		if (request.action() == ProtectionAction.ENTRY) return ProtectionDecision.ALLOW;
		boolean member = request.actor().type() == com.voluble.titanMC.regions.protection.model.ProtectionActor.Type.PLAYER
			&& region.access().isMember(request.actor().playerId());
		if (member && MEMBER_ACTIONS.contains(request.action())) return ProtectionDecision.ALLOW;
		return ProtectionDecision.DENY;
	}
}
