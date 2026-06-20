package com.voluble.titanMC.regions.protection;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.model.TransitionRule;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.ProtectionEvaluationContext;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyEvaluator;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.policy.RegionProtectionPolicy;
import com.voluble.titanMC.regions.protection.service.ProtectionEvaluation;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionEvaluationTest {

	private static final WorldId WORLD = new WorldId(new UUID(5L, 1L));
	private static final ProtectionActor ACTOR = ProtectionActor.player(new UUID(5L, 2L), Set.of());
	private static final BlockPosition INSIDE = new BlockPosition(WORLD, 5, 5, 5);
	private static final BlockPosition OUTSIDE = new BlockPosition(WORLD, 30, 5, 5);

	@Test
	void evaluationFreezesPolicyStateAndTime() {
		AtomicReference<ProtectionDecision> current = new AtomicReference<>(ProtectionDecision.ALLOW);
		SnapshotPolicy policy = new SnapshotPolicy(current);
		ProtectionService service = service(policy);
		Instant openedAt = Instant.parse("2026-06-20T12:00:00Z");

		ProtectionEvaluation evaluation = service.beginEvaluation(ACTOR, openedAt);
		current.set(ProtectionDecision.DENY);

		assertEquals(openedAt, evaluation.evaluatedAt());
		assertTrue(evaluation.resolve(at(INSIDE)).allowed());
		assertFalse(service.resolve(at(INSIDE)).allowed());
	}

	@Test
	void transitionRulesPreserveBothUnderlyingResolutions() {
		AtomicReference<ProtectionDecision> current = new AtomicReference<>(ProtectionDecision.DENY);
		ProtectionEvaluation evaluation = service(new SnapshotPolicy(current)).beginEvaluation(ACTOR, Instant.EPOCH);
		ProtectionRequest movement = ProtectionRequest.moving(
			ACTOR, ProtectionAction.PISTON_MOVE, OUTSIDE, INSIDE
		);

		assertTrue(evaluation.resolveTransition(movement, TransitionRule.SOURCE).allowed());
		assertFalse(evaluation.resolveTransition(movement, TransitionRule.TARGET).allowed());
		assertFalse(evaluation.resolveTransition(movement, TransitionRule.BOTH).allowed());
		assertTrue(evaluation.resolveTransition(movement, TransitionRule.EITHER).allowed());
		assertTrue(evaluation.resolveTransition(movement, TransitionRule.BOTH).source().isPresent());
		assertTrue(evaluation.resolveTransition(movement, TransitionRule.BOTH).target().isPresent());
	}

	private static ProtectionService service(RegionProtectionPolicy policy) {
		RegionDefinition region = new RegionDefinition(
			new RegionId(new UUID(5L, 3L)), RegionKey.of("cell", "alpha"), WORLD, 200,
			new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16)), Instant.EPOCH, Instant.EPOCH
		);
		RegionPolicyRegistry registry = RegionPolicyRegistry.builder().register(policy).build();
		return new ProtectionService(
			(worldId, x, y, z) -> region.contains(x, y, z) ? List.of(region) : List.of(),
			registry,
			request -> ProtectionDecision.ALLOW,
			ProtectionBypass.none()
		);
	}

	private static ProtectionRequest at(BlockPosition position) {
		return ProtectionRequest.at(ACTOR, ProtectionAction.BLOCK_BREAK, position);
	}

	private record SnapshotPolicy(AtomicReference<ProtectionDecision> current) implements RegionProtectionPolicy {
		@Override public String id() { return "snapshot-cell"; }
		@Override public String namespace() { return "cell"; }

		@Override
		public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) {
			return current.get();
		}

		@Override
		public RegionPolicyEvaluator openEvaluation(ProtectionEvaluationContext context) {
			ProtectionDecision frozen = current.get();
			return (request, region) -> frozen;
		}
	}
}
