package com.voluble.titanMC.regions.admin;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.BlockPoint2;
import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.PolygonPrismGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.policy.WorldProtectionDefaults;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import com.voluble.titanMC.regions.service.RegionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionAdminServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void managesCustomRegionsWithoutLosingFlagsOnRedefine() throws Exception {
		WorldId world = new WorldId(UUID.randomUUID());
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("admin.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"yard", world, 250, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.setFlag(
				"yard", world, ProtectionAction.PLAYER_PVP, ProtectionDecision.ALLOW
			).successful());
			assertTrue(admin.redefine(
				"yard", world, new CuboidGeometry(new BlockBox(20, 0, 20, 30, 10, 30))
			).successful());
			RegionDefinition region = admin.find(world, "yard");
			assertEquals(RegionKey.of("custom", "yard"), region.key());
			assertEquals(250, region.priority());
			assertEquals(ProtectionDecision.ALLOW, region.flags().decision(ProtectionAction.PLAYER_PVP));
			assertEquals(1, admin.list(world).size());
		}
	}

	@Test
	void blockPlaceAllowWorksInSeparateCuboidAndPolygonRegions() throws Exception {
		WorldId world = new WorldId(UUID.randomUUID());
		ProtectionActor player = ProtectionActor.player(UUID.randomUUID(), Set.of());
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("flags.db"))) {
			RegionAdminService admin = new RegionAdminService(engine);
			assertTrue(admin.create(
				"cube", world, 100, new CuboidGeometry(new BlockBox(0, 0, 0, 10, 10, 10))
			).successful());
			assertTrue(admin.create(
				"poly", world, 10, new PolygonPrismGeometry(
					List.of(new BlockPoint2(20, 20), new BlockPoint2(30, 20), new BlockPoint2(20, 30)),
					0,
					9
				)
			).successful());
			assertTrue(admin.setFlag(
				"cube", world, ProtectionAction.BLOCK_PLACE, ProtectionDecision.ALLOW
			).successful());
			assertTrue(admin.setFlag(
				"poly", world, ProtectionAction.BLOCK_PLACE, ProtectionDecision.ALLOW
			).successful());

			ProtectionService protection = ProtectionService.forEngine(
				engine,
				RegionPolicyRegistry.builder().build(),
				WorldProtectionDefaults.builder()
					.worldDefault(world, ProtectionDecision.DENY)
					.build(),
				ProtectionBypass.none()
			);

			assertTrue(protection.allowed(place(player, world, 5, 5, 5)));
			assertTrue(protection.allowed(place(player, world, 22, 5, 22)));
			assertEquals(
				ProtectionDecision.DENY,
				protection.resolve(place(player, world, 40, 5, 40)).decision()
			);
		}
	}

	private static ProtectionRequest place(
		ProtectionActor actor,
		WorldId world,
		int x,
		int y,
		int z
	) {
		return ProtectionRequest.at(
			actor, ProtectionAction.BLOCK_PLACE, new BlockPosition(world, x, y, z)
		);
	}
}
