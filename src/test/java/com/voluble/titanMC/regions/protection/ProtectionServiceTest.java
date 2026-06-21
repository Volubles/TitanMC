package com.voluble.titanMC.regions.protection;

import com.voluble.titanMC.regions.index.RegionIndexOptions;
import com.voluble.titanMC.regions.index.RegionIndexSnapshot;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.model.ProtectionResolution;
import com.voluble.titanMC.regions.protection.model.RegionFlagSet;
import com.voluble.titanMC.regions.protection.model.RegionSubject;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.policy.RegionProtectionPolicy;
import com.voluble.titanMC.regions.protection.policy.WorldProtectionDefaults;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionServiceTest {

	private static final WorldId WORLD = new WorldId(new UUID(1L, 1L));
	private static final WorldId OTHER_WORLD = new WorldId(new UUID(1L, 2L));
	private static final ProtectionActor PLAYER = ProtectionActor.player(new UUID(2L, 1L), Set.of());
	private static final BlockPosition POSITION = new BlockPosition(WORLD, 5, 5, 5);

	@Test
	void higherPriorityExplicitDecisionWinsOverLowerPriority() throws Exception {
		RegionDefinition high = region(1, "cell", "high", 200);
		RegionDefinition low = region(2, "mine", "low", 100);
		ProtectionService service = service(
			List.of(low, high),
			new FixedPolicy("cell-policy", "cell", ProtectionDecision.ALLOW),
			new FixedPolicy("mine-policy", "mine", ProtectionDecision.DENY)
		);

		ProtectionResolution resolution = service.resolve(request());

		assertEquals(ProtectionDecision.ALLOW, resolution.decision());
		assertEquals(ProtectionResolution.Reason.REGION_POLICY, resolution.reason());
		assertEquals(200, resolution.decidingPriority().orElseThrow());
		assertEquals(List.of(high.id()), resolution.evaluations().stream().map(evaluation -> evaluation.regionId()).toList());
	}

	@Test
	void denyWinsWhenPoliciesDisagreeAtSamePriority() throws Exception {
		RegionDefinition cell = region(1, "cell", "alpha", 100);
		RegionDefinition mine = region(2, "mine", "beta", 100);
		ProtectionService service = service(
			List.of(mine, cell),
			new FixedPolicy("cell-policy", "cell", ProtectionDecision.ALLOW),
			new FixedPolicy("mine-policy", "mine", ProtectionDecision.DENY)
		);

		ProtectionResolution resolution = service.resolve(request());

		assertEquals(ProtectionDecision.DENY, resolution.decision());
		assertEquals(List.of(cell.id(), mine.id()), resolution.evaluations().stream().map(evaluation -> evaluation.regionId()).toList());
	}

	@Test
	void explicitRegionFlagOverridesNamespacePolicy() throws Exception {
		RegionDefinition region = region(1, "mine", "alpha", 100, RegionFlagSet.empty()
			.with(ProtectionAction.BLOCK_BREAK, ProtectionDecision.ALLOW));
		ProtectionService service = service(
			List.of(region),
			new FixedPolicy("mine-policy", "mine", ProtectionDecision.DENY)
		);

		ProtectionResolution resolution = service.resolve(request());

		assertEquals(ProtectionDecision.ALLOW, resolution.decision());
		assertEquals("region-flags", resolution.evaluations().getFirst().policyId());
	}

	@Test
	void ownerAndVaultGroupScopesResolveWithoutUnnecessaryGroupQueries() throws Exception {
		UUID ownerId = PLAYER.playerId();
		RegionFlagSet flags = RegionFlagSet.empty()
			.with(ProtectionAction.BLOCK_BREAK, RegionSubject.EVERYONE, ProtectionDecision.DENY)
			.with(ProtectionAction.BLOCK_BREAK, RegionSubject.OWNERS, ProtectionDecision.ALLOW)
			.with(ProtectionAction.CONTAINER_OPEN, RegionSubject.group("vip"), ProtectionDecision.ALLOW);
		RegionDefinition region = region(
			1, "custom", "cell", 100, RegionAccessSet.of(Set.of(ownerId), Set.of()), flags
		);
		AtomicInteger groupQueries = new AtomicInteger();
		ProtectionService service = new ProtectionService(
			snapshot(List.of(region))::findAll,
			RegionPolicyRegistry.builder().build(),
			WorldProtectionDefaults.builder().fallback(ProtectionDecision.DENY).build(),
			ProtectionBypass.none(),
			(actor, worldId, group) -> {
				groupQueries.incrementAndGet();
				return group.equals("vip");
			}
		);

		ProtectionResolution ownerResolution = service.resolve(request());

		assertEquals(ProtectionDecision.ALLOW, ownerResolution.decision());
		assertEquals("region-flags:owners", ownerResolution.evaluations().getFirst().policyId());
		assertEquals(0, groupQueries.get());

		ProtectionResolution groupResolution = service.resolve(
			ProtectionRequest.at(PLAYER, ProtectionAction.CONTAINER_OPEN, POSITION)
		);

		assertEquals(ProtectionDecision.ALLOW, groupResolution.decision());
		assertEquals(1, groupQueries.get());
	}

	@Test
	void abstainContinuesToTheNextPriorityLevel() throws Exception {
		RegionDefinition high = region(1, "cell", "high", 200);
		RegionDefinition low = region(2, "mine", "low", 100);
		ProtectionService service = service(
			List.of(high, low),
			new FixedPolicy("cell-policy", "cell", ProtectionDecision.ABSTAIN),
			new FixedPolicy("mine-policy", "mine", ProtectionDecision.ALLOW)
		);

		ProtectionResolution resolution = service.resolve(request());

		assertEquals(ProtectionDecision.ALLOW, resolution.decision());
		assertEquals(100, resolution.decidingPriority().orElseThrow());
		assertEquals(2, resolution.evaluations().size());
	}

	@Test
	void unregisteredNamespaceAbstainsAndWorldDefaultDecides() throws Exception {
		RegionDefinition unknown = region(1, "unknown", "alpha", 999);
		ProtectionService service = service(List.of(unknown));

		ProtectionResolution resolution = service.resolve(request());

		assertEquals(ProtectionDecision.DENY, resolution.decision());
		assertEquals(ProtectionResolution.Reason.WORLD_DEFAULT, resolution.reason());
		assertEquals(ProtectionDecision.ABSTAIN, resolution.evaluations().getFirst().decision());
	}

	@Test
	void configuredWorldDefaultAndFallbackAreIndependent() throws Exception {
		ProtectionService service = service(List.of());

		assertFalse(service.allowed(request()));
		ProtectionRequest otherWorld = ProtectionRequest.at(
			PLAYER,
			ProtectionAction.BLOCK_BREAK,
			new BlockPosition(OTHER_WORLD, 5, 5, 5)
		);
		assertTrue(service.allowed(otherWorld));
	}

	@Test
	void bypassShortCircuitsRegionLookup() {
		ProtectionActor admin = ProtectionActor.player(new UUID(2L, 2L), Set.of("TITANMC.PROTECTION.BYPASS"));
		ProtectionService service = new ProtectionService(
			(world, x, y, z) -> { throw new AssertionError("lookup must not run for bypass"); },
			RegionPolicyRegistry.builder().build(),
			request -> ProtectionDecision.DENY,
			ProtectionBypass.permission("titanmc.protection.bypass")
		);

		ProtectionResolution resolution = service.resolve(ProtectionRequest.at(admin, ProtectionAction.BLOCK_BREAK, POSITION));

		assertEquals(ProtectionDecision.ALLOW, resolution.decision());
		assertEquals(ProtectionResolution.Reason.BYPASS, resolution.reason());
	}

	@Test
	void policyExceptionsAndNullResultsFailClosed() throws Exception {
		RegionDefinition exploding = region(1, "cell", "alpha", 100);
		RegionProtectionPolicy throwingPolicy = new RegionProtectionPolicy() {
			@Override public String id() { return "throwing"; }
			@Override public String namespace() { return "cell"; }
			@Override public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) {
				throw new IllegalStateException("broken policy");
			}
		};
		ProtectionService throwing = service(List.of(exploding), throwingPolicy);

		ProtectionResolution throwingResolution = throwing.resolve(request());
		assertEquals(ProtectionDecision.DENY, throwingResolution.decision());
		assertEquals(ProtectionResolution.Reason.POLICY_ERROR, throwingResolution.reason());
		assertTrue(throwingResolution.evaluations().getFirst().error().isPresent());

		RegionProtectionPolicy nullPolicy = new RegionProtectionPolicy() {
			@Override public String id() { return "null-policy"; }
			@Override public String namespace() { return "cell"; }
			@Override public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) { return null; }
		};
		ProtectionResolution nullResolution = service(List.of(exploding), nullPolicy).resolve(request());
		assertEquals(ProtectionDecision.DENY, nullResolution.decision());
		assertEquals(ProtectionResolution.Reason.POLICY_ERROR, nullResolution.reason());
	}

	@Test
	void invalidDefaultsAndBypassFailuresFailClosed() throws Exception {
		ProtectionService abstainingDefault = new ProtectionService(
			snapshot(List.of())::findAll,
			RegionPolicyRegistry.builder().build(),
			request -> ProtectionDecision.ABSTAIN,
			ProtectionBypass.none()
		);
		ProtectionResolution defaultResolution = abstainingDefault.resolve(request());
		assertEquals(ProtectionDecision.DENY, defaultResolution.decision());
		assertEquals(ProtectionResolution.Reason.DEFAULT_ERROR, defaultResolution.reason());

		ProtectionService brokenBypass = new ProtectionService(
			snapshot(List.of())::findAll,
			RegionPolicyRegistry.builder().build(),
			request -> ProtectionDecision.ALLOW,
			request -> { throw new IllegalStateException("broken bypass"); }
		);
		ProtectionResolution bypassResolution = brokenBypass.resolve(request());
		assertEquals(ProtectionDecision.DENY, bypassResolution.decision());
		assertEquals(ProtectionResolution.Reason.BYPASS_ERROR, bypassResolution.reason());
	}

	@Test
	void lookupFailuresAndNullResultsFailClosed() {
		RegionPolicyRegistry registry = RegionPolicyRegistry.builder().build();
		ProtectionService throwing = new ProtectionService(
			(world, x, y, z) -> { throw new IllegalStateException("broken lookup"); },
			registry,
			request -> ProtectionDecision.ALLOW,
			ProtectionBypass.none()
		);
		ProtectionResolution throwingResolution = throwing.resolve(request());
		assertEquals(ProtectionDecision.DENY, throwingResolution.decision());
		assertEquals(ProtectionResolution.Reason.LOOKUP_ERROR, throwingResolution.reason());

		ProtectionService returningNull = new ProtectionService(
			(world, x, y, z) -> null,
			registry,
			request -> ProtectionDecision.ALLOW,
			ProtectionBypass.none()
		);
		ProtectionResolution nullResolution = returningNull.resolve(request());
		assertEquals(ProtectionDecision.DENY, nullResolution.decision());
		assertEquals(ProtectionResolution.Reason.LOOKUP_ERROR, nullResolution.reason());
	}

	@Test
	void sourceAndTargetMustShareAWorld() {
		assertThrows(IllegalArgumentException.class, () -> ProtectionRequest.moving(
			PLAYER,
			ProtectionAction.PISTON_MOVE,
			new BlockPosition(WORLD, 0, 0, 0),
			new BlockPosition(OTHER_WORLD, 1, 0, 0)
		));
	}

	private static ProtectionService service(List<RegionDefinition> regions, RegionProtectionPolicy... policies) throws Exception {
		RegionPolicyRegistry.Builder registry = RegionPolicyRegistry.builder();
		for (RegionProtectionPolicy policy : policies) registry.register(policy);
		WorldProtectionDefaults defaults = WorldProtectionDefaults.builder()
			.fallback(ProtectionDecision.ALLOW)
			.worldDefault(WORLD, ProtectionDecision.DENY)
			.build();
		return new ProtectionService(
			snapshot(regions)::findAll,
			registry.build(),
			defaults,
			ProtectionBypass.permission("titanmc.protection.bypass")
		);
	}

	private static RegionIndexSnapshot snapshot(List<RegionDefinition> regions) throws Exception {
		return RegionIndexSnapshot.build(1L, regions, RegionIndexOptions.defaults());
	}

	private static ProtectionRequest request() {
		return ProtectionRequest.at(PLAYER, ProtectionAction.BLOCK_BREAK, POSITION);
	}

	private static RegionDefinition region(long id, String namespace, String name, int priority) {
		return region(id, namespace, name, priority, RegionFlagSet.empty());
	}

	private static RegionDefinition region(
		long id,
		String namespace,
		String name,
		int priority,
		RegionFlagSet flags
	) {
		return new RegionDefinition(
			new RegionId(new UUID(0L, id)),
			RegionKey.of(namespace, name),
			WORLD,
			priority,
			new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16)),
			flags,
			Instant.EPOCH,
			Instant.EPOCH
		);
	}

	private static RegionDefinition region(
		long id,
		String namespace,
		String name,
		int priority,
		RegionAccessSet access,
		RegionFlagSet flags
	) {
		return new RegionDefinition(
			new RegionId(new UUID(0L, id)),
			RegionKey.of(namespace, name),
			WORLD,
			priority,
			new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16)),
			access,
			flags,
			com.voluble.titanMC.regions.model.RegionTextSet.empty(),
			Instant.EPOCH,
			Instant.EPOCH,
			1L
		);
	}

	private record FixedPolicy(
		String id,
		String namespace,
		ProtectionDecision fixedDecision
	) implements RegionProtectionPolicy {
		@Override
		public ProtectionDecision decide(ProtectionRequest request, RegionDefinition region) {
			return fixedDecision;
		}
	}
}
