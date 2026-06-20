package com.voluble.titanMC.mines.protection;

import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.policy.ActionRuleSet;
import com.voluble.titanMC.regions.protection.policy.RegionProtectionPolicy;

public final class MineProtectionPolicy implements RegionProtectionPolicy {

	public static final String NAMESPACE = "mine";

	private static final ActionRuleSet RULES = ActionRuleSet.builder()
		.allow(ProtectionAction.BLOCK_BREAK)
		.deny(
			ProtectionAction.BLOCK_PLACE,
			ProtectionAction.PRESSURE_PLATE_TRIGGER,
			ProtectionAction.CONTAINER_OPEN,
			ProtectionAction.BUCKET_FILL,
			ProtectionAction.BUCKET_EMPTY,
			ProtectionAction.EXPLOSION_BLOCK_DAMAGE,
			ProtectionAction.PISTON_MOVE
		)
		.build();

	@Override
	public String id() {
		return "mine-default";
	}

	@Override
	public String namespace() {
		return NAMESPACE;
	}

	@Override
	public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) {
		return RULES.decision(request.action());
	}
}
