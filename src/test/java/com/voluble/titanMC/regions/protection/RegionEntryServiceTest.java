package com.voluble.titanMC.regions.protection;

import com.voluble.titanMC.regions.admin.RegionAdminService;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionTextFlag;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.RegionSubject;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.service.RegionEntryService;
import com.voluble.titanMC.regions.service.RegionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEntryServiceTest {

	@TempDir
	Path temporaryDirectory;

	private final WorldId world = new WorldId(UUID.randomUUID());
	private final ProtectionActor player = ProtectionActor.player(UUID.randomUUID(), Set.of());

	@Test
	void entryIsAllowedByDefaultAndMessagesFollowActualMembershipChanges() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("messages.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"spawn", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.setText(
				"spawn", world, RegionTextFlag.ENTRY_MESSAGE, "<green>Welcome</green>"
			).successful());
			assertTrue(admin.setText(
				"spawn", world, RegionTextFlag.EXIT_MESSAGE, "<yellow>Goodbye</yellow>"
			).successful());
			RegionEntryService service = new RegionEntryService(engine, ProtectionBypass.none());

			var entering = service.evaluate(player, position(-1), position(1));
			var remainingInside = service.evaluate(player, position(1), position(2));
			var exiting = service.evaluate(player, position(2), position(11));

			assertTrue(entering.allowed());
			assertEquals("<green>Welcome</green>", entering.entryMessage().orElseThrow());
			assertTrue(remainingInside.entered().isEmpty());
			assertTrue(remainingInside.entryMessage().isEmpty());
			assertEquals("<yellow>Goodbye</yellow>", exiting.exitMessage().orElseThrow());
		}
	}

	@Test
	void onlyNewlyEnteredNestedRegionsParticipateInEntryDecision() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("nested.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"outer", world, 200, new CuboidGeometry(new BlockBox(0, 0, 0, 20, 10, 20))
			).successful());
			assertTrue(admin.create(
				"inner", world, 100, new CuboidGeometry(new BlockBox(5, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.setFlag(
				"outer", world, ProtectionAction.ENTRY, ProtectionDecision.DENY
			).successful());
			assertTrue(admin.setFlag(
				"inner", world, ProtectionAction.ENTRY, ProtectionDecision.ALLOW
			).successful());
			RegionEntryService service = new RegionEntryService(engine, ProtectionBypass.none());

			var intoOuter = service.evaluate(player, position(-1), position(1));
			var outerToInner = service.evaluate(player, position(1), position(6));

			assertEquals(ProtectionDecision.DENY, intoOuter.decision());
			assertEquals(ProtectionDecision.ALLOW, outerToInner.decision());
			assertEquals("inner", outerToInner.entered().getFirst().key().name());
		}
	}

	@Test
	void higherPriorityWinsAndDenyWinsAtEqualPriority() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("priority.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			CuboidGeometry geometry = new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10));
			assertTrue(admin.create("low-deny", world, 100, geometry).successful());
			assertTrue(admin.create("high-allow", world, 200, geometry).successful());
			assertTrue(admin.setFlag(
				"low-deny", world, ProtectionAction.ENTRY, ProtectionDecision.DENY
			).successful());
			assertTrue(admin.setFlag(
				"high-allow", world, ProtectionAction.ENTRY, ProtectionDecision.ALLOW
			).successful());
			RegionEntryService service = new RegionEntryService(engine, ProtectionBypass.none());

			assertEquals(ProtectionDecision.ALLOW, service.evaluate(player, position(-1), position(1)).decision());
			assertTrue(admin.setPriority("low-deny", world, 200).successful());
			assertEquals(ProtectionDecision.DENY, service.evaluate(player, position(-1), position(1)).decision());
		}
	}

	@Test
	void bypassAllowsEntryAndCustomDenyMessageComesFromWinningRegion() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("bypass.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"private", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.setFlag(
				"private", world, ProtectionAction.ENTRY, ProtectionDecision.DENY
			).successful());
			assertTrue(admin.setText(
				"private", world, RegionTextFlag.ENTRY_DENY_MESSAGE, "<red>Private!</red>"
			).successful());
			RegionEntryService normal = new RegionEntryService(engine, ProtectionBypass.none());
			RegionEntryService bypassing = new RegionEntryService(
				engine, request -> request.actor().identifier().equals("admin")
			);

			assertEquals(
				"<red>Private!</red>",
				normal.evaluate(player, position(-1), position(1)).denyMessage().orElseThrow()
			);
			assertTrue(bypassing.evaluate(
				ProtectionActor.system("admin", Set.of()), position(-1), position(1)
			).allowed());
		}
	}

	@Test
	void reusesCachedSourceMembershipUntilTheRegionSnapshotChanges() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("membership-cache.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"spawn", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			RegionEntryService service = new RegionEntryService(engine, ProtectionBypass.none());

			var first = service.movementMembership(position(-2), position(-1), null);
			var second = service.movementMembership(position(-1), position(0), first.target());

			assertSame(first.target(), second.source());
			assertTrue(admin.setText(
				"spawn", world, RegionTextFlag.ENTRY_MESSAGE, "Updated"
			).successful());

			var afterUpdate = service.movementMembership(position(0), position(1), second.target());

			assertNotSame(second.target(), afterUpdate.source());
			assertTrue(afterUpdate.source().version() > second.target().version());
		}
	}

	@Test
	void doesNotCreateAnActorUnlessAWinningEntryFlagDeniesMovement() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("lazy-actor.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"spawn", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			RegionEntryService service = new RegionEntryService(engine, ProtectionBypass.none());
			AtomicInteger actorCreations = new AtomicInteger();

			var allowed = service.evaluate(
				() -> {
					actorCreations.incrementAndGet();
					return player;
				},
				service.membership(position(-1)),
				service.membership(position(1))
			);

			assertTrue(allowed.allowed());
			assertEquals(0, actorCreations.get());

			assertTrue(admin.setFlag(
				"spawn", world, ProtectionAction.ENTRY, ProtectionDecision.DENY
			).successful());
			var denied = service.evaluate(
				() -> {
					actorCreations.incrementAndGet();
					return player;
				},
				service.membership(position(-1)),
				service.membership(position(1))
			);

			assertEquals(ProtectionDecision.DENY, denied.decision());
			assertEquals(1, actorCreations.get());
		}
	}

	@Test
	void scopedEntryRulesUseOwnersAndGroups() throws Exception {
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("scoped-entry.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"cell", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.addOwner("cell", world, player.playerId()).successful());
			assertTrue(admin.setFlag(
				"cell", world, ProtectionAction.ENTRY, RegionSubject.EVERYONE, ProtectionDecision.DENY
			).successful());
			assertTrue(admin.setFlag(
				"cell", world, ProtectionAction.ENTRY, RegionSubject.OWNERS, ProtectionDecision.ALLOW
			).successful());
			RegionEntryService ownerService = new RegionEntryService(engine, ProtectionBypass.none());

			assertEquals(
				ProtectionDecision.ALLOW,
				ownerService.evaluate(player, position(-1), position(1)).decision()
			);

			ProtectionActor guard = ProtectionActor.player(UUID.randomUUID(), Set.of());
			assertTrue(admin.setFlag(
				"cell", world, ProtectionAction.ENTRY, RegionSubject.group("guard"), ProtectionDecision.ALLOW
			).successful());
			RegionEntryService groupService = new RegionEntryService(
				engine,
				ProtectionBypass.none(),
				(actor, checkedWorld, group) ->
					actor.playerId().equals(guard.playerId()) && group.equals("guard")
			);

			assertEquals(
				ProtectionDecision.ALLOW,
				groupService.evaluate(guard, position(-1), position(1)).decision()
			);
		}
	}

	private BlockPosition position(int x) {
		return new BlockPosition(world, x, 1, 1);
	}
}
