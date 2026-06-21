package com.voluble.titanMC.regions.service;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.BlockPoint2;
import com.voluble.titanMC.regions.model.ConvexPolyhedronGeometry;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.PolygonPrismGeometry;
import com.voluble.titanMC.regions.model.PolyhedronPlane;
import com.voluble.titanMC.regions.model.RegionGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.RegionFlagSet;
import com.voluble.titanMC.regions.protection.model.RegionSubject;
import com.voluble.titanMC.regions.model.RegionTextFlag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEnginePersistenceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void concurrentSubmissionsAreSerializedAndSurviveRestart() throws Exception {
		Path database = temporaryDirectory.resolve("regions.db");
		WorldId world = new WorldId(UUID.randomUUID());
		List<CompletableFuture<RegionMutationResult>> writes = new ArrayList<>();

		try (RegionEngine engine = RegionEngine.open(database)) {
			for (int index = 0; index < 100; index++) {
				writes.add(engine.create(
					RegionKey.of("cell", "cell_" + index),
					world,
					index % 5,
					new CuboidGeometry(new BlockBox(index * 32, 0, 0, index * 32 + 16, 16, 16))
				));
			}
			CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
			assertTrue(writes.stream().map(CompletableFuture::join).allMatch(RegionMutationResult::successful));
			assertEquals(100, engine.snapshot().definitions().size());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(100, reloaded.snapshot().definitions().size());
			assertEquals("cell_42", reloaded.findAll(world, 42 * 32, 1, 1).getFirst().key().name());
		}
	}

	@Test
	void duplicateKeyFailureDoesNotMutateSnapshotOrDatabase() throws Exception {
		Path database = temporaryDirectory.resolve("duplicate.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("cell", "alpha");

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionMutationResult first = engine.create(key, world, 0, new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))).join();
			RegionMutationResult duplicate = engine.create(key, world, 0, new CuboidGeometry(new BlockBox(32, 0, 0, 48, 16, 16))).join();
			assertInstanceOf(RegionMutationResult.Success.class, first);
			RegionMutationResult.Failure failure = assertInstanceOf(RegionMutationResult.Failure.class, duplicate);
			assertEquals(RegionMutationResult.Reason.DUPLICATE_KEY, failure.reason());
			assertEquals(1, engine.snapshot().definitions().size());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(1, reloaded.snapshot().definitions().size());
			RegionDefinition stored = reloaded.find(world, key);
			assertEquals(new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16)), stored.geometry());
		}
	}

	@Test
	void polygonAndPolyhedronGeometrySurviveRestart() throws Exception {
		Path database = temporaryDirectory.resolve("shapes.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionGeometry polygon = new PolygonPrismGeometry(
			List.of(new BlockPoint2(0, 0), new BlockPoint2(12, 0), new BlockPoint2(0, 12)),
			-10,
			20
		);
		RegionGeometry polyhedron = new ConvexPolyhedronGeometry(
			new BlockBox(20, 0, 20, 31, 11, 31),
			List.of(
				new PolyhedronPlane(1, 0, 0, 30),
				new PolyhedronPlane(-1, 0, 0, -20),
				new PolyhedronPlane(0, 1, 0, 10),
				new PolyhedronPlane(0, -1, 0, 0),
				new PolyhedronPlane(0, 0, 1, 30),
				new PolyhedronPlane(0, 0, -1, -20)
			)
		);

		try (RegionEngine engine = RegionEngine.open(database)) {
			assertTrue(engine.create(RegionKey.of("custom", "polygon"), world, 0, polygon).join().successful());
			assertTrue(engine.create(RegionKey.of("custom", "polyhedron"), world, 0, polyhedron).join().successful());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(polygon, reloaded.find(world, RegionKey.of("custom", "polygon")).geometry());
			assertEquals(polyhedron, reloaded.find(world, RegionKey.of("custom", "polyhedron")).geometry());
		}
	}

	@Test
	void regionFlagsSurviveUpdatesAndRestart() throws Exception {
		Path database = temporaryDirectory.resolve("flags.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("custom", "yard");
		RegionFlagSet flags = RegionFlagSet.empty()
			.with(ProtectionAction.PLAYER_PVP, ProtectionDecision.ALLOW)
			.with(ProtectionAction.BLOCK_BREAK, ProtectionDecision.DENY);

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition created = assertInstanceOf(
				RegionMutationResult.Success.class,
				engine.create(key, world, 0, new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))).join()
			).region();
			assertTrue(engine.setFlags(created.id(), created.revision(), flags).join().successful());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(flags, reloaded.find(world, key).flags());
		}
	}

	@Test
	void scopedFlagsAndRegionAccessSurviveRestart() throws Exception {
		Path database = temporaryDirectory.resolve("access.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("custom", "cell");
		UUID owner = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		RegionAccessSet access = RegionAccessSet.of(Set.of(owner), Set.of(member));
		RegionFlagSet flags = RegionFlagSet.empty()
			.with(ProtectionAction.CONTAINER_OPEN, RegionSubject.EVERYONE, ProtectionDecision.DENY)
			.with(ProtectionAction.CONTAINER_OPEN, RegionSubject.MEMBERS, ProtectionDecision.ALLOW)
			.with(ProtectionAction.ENTRY, RegionSubject.group("guard"), ProtectionDecision.ALLOW);

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition created = assertInstanceOf(
				RegionMutationResult.Success.class,
				engine.create(key, world, 0, new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))).join()
			).region();
			assertTrue(engine.setSecurity(
				created.id(), created.revision(), access, flags
			).join().successful());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			RegionDefinition stored = reloaded.find(world, key);
			assertEquals(access, stored.access());
			assertEquals(flags, stored.flags());
		}
	}

	@Test
	void regionTextFlagsSurviveUpdatesAndRestart() throws Exception {
		Path database = temporaryDirectory.resolve("text-flags.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("custom", "lobby");

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition created = assertInstanceOf(
				RegionMutationResult.Success.class,
				engine.create(key, world, 0, new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))).join()
			).region();
			assertTrue(engine.setText(
				created.id(),
				created.revision(),
				created.text()
					.with(RegionTextFlag.ENTRY_MESSAGE, "<green>Welcome</green>")
					.with(RegionTextFlag.EXIT_MESSAGE, "Goodbye")
			).join().successful());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			RegionDefinition stored = reloaded.find(world, key);
			assertEquals(
				"<green>Welcome</green>",
				stored.text().value(RegionTextFlag.ENTRY_MESSAGE).orElseThrow()
			);
			assertEquals("Goodbye", stored.text().value(RegionTextFlag.EXIT_MESSAGE).orElseThrow());
		}
	}

	@Test
	void schemaFiveDatabaseMigratesWithoutLosingExistingRegions() throws Exception {
		Path database = temporaryDirectory.resolve("schema-five.db");
		createEmptySchemaFiveDatabase(database);

		try (RegionEngine engine = RegionEngine.open(database)) {
			assertTrue(engine.snapshot().definitions().isEmpty());
			assertTrue(engine.create(
				RegionKey.of("custom", "after_migration"),
				new WorldId(UUID.randomUUID()),
				100,
				new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))
			).join().successful());
		}

		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement();
			 var result = statement.executeQuery("PRAGMA user_version")) {
			assertTrue(result.next());
			assertEquals(7, result.getInt(1));
		}
	}

	@Test
	void schemaSixFlagsMigrateToEveryoneScopeWithoutChangingBehavior() throws Exception {
		Path database = temporaryDirectory.resolve("schema-six.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("custom", "legacy");
		createSchemaSixDatabaseWithFlag(database, world, key);

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionDefinition stored = engine.find(world, key);
			assertEquals(
				ProtectionDecision.DENY,
				stored.flags().decision(ProtectionAction.BLOCK_BREAK, RegionSubject.EVERYONE)
			);
			assertTrue(stored.access().owners().isEmpty());
			assertTrue(stored.access().members().isEmpty());
		}
	}

	private static void createEmptySchemaFiveDatabase(Path database) throws Exception {
		Class.forName("org.sqlite.JDBC");
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				CREATE TABLE regions (
				    id TEXT PRIMARY KEY NOT NULL,
				    world_id TEXT NOT NULL,
				    namespace TEXT NOT NULL,
				    name TEXT NOT NULL,
				    priority INTEGER NOT NULL,
				    revision INTEGER NOT NULL,
				    created_at INTEGER NOT NULL,
				    updated_at INTEGER NOT NULL,
				    UNIQUE(world_id, namespace, name)
				)
				""");
			statement.executeUpdate("""
				CREATE TABLE region_geometries (
				    region_id TEXT PRIMARY KEY NOT NULL,
				    geometry_type TEXT NOT NULL,
				    min_x INTEGER NOT NULL,
				    min_y INTEGER NOT NULL,
				    min_z INTEGER NOT NULL,
				    max_x_exclusive INTEGER NOT NULL,
				    max_y_exclusive INTEGER NOT NULL,
				    max_z_exclusive INTEGER NOT NULL,
				    FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE
				)
				""");
			statement.executeUpdate("""
				CREATE TABLE region_polygon_points (
				    region_id TEXT NOT NULL,
				    point_order INTEGER NOT NULL,
				    x INTEGER NOT NULL,
				    z INTEGER NOT NULL,
				    PRIMARY KEY(region_id, point_order),
				    FOREIGN KEY(region_id) REFERENCES region_geometries(region_id) ON DELETE CASCADE
				)
				""");
			statement.executeUpdate("""
				CREATE TABLE region_polyhedron_planes (
				    region_id TEXT NOT NULL,
				    plane_order INTEGER NOT NULL,
				    normal_x REAL NOT NULL,
				    normal_y REAL NOT NULL,
				    normal_z REAL NOT NULL,
				    maximum_dot_product REAL NOT NULL,
				    PRIMARY KEY(region_id, plane_order),
				    FOREIGN KEY(region_id) REFERENCES region_geometries(region_id) ON DELETE CASCADE
				)
				""");
			statement.executeUpdate("""
				CREATE TABLE region_flags (
				    region_id TEXT NOT NULL,
				    action TEXT NOT NULL,
				    decision TEXT NOT NULL,
				    PRIMARY KEY(region_id, action),
				    FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE
				)
				""");
			statement.execute("PRAGMA user_version = 5");
		}
	}

	private static void createSchemaSixDatabaseWithFlag(
		Path database,
		WorldId world,
		RegionKey key
	) throws Exception {
		createEmptySchemaFiveDatabase(database);
		String id = UUID.randomUUID().toString();
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				CREATE TABLE region_text_flags (
				    region_id TEXT NOT NULL,
				    text_flag TEXT NOT NULL,
				    value TEXT NOT NULL,
				    PRIMARY KEY(region_id, text_flag),
				    FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE
				)
				""");
			long now = System.currentTimeMillis();
			statement.executeUpdate(
				"INSERT INTO regions(id, world_id, namespace, name, priority, revision, created_at, updated_at) VALUES ('"
					+ id + "', '" + world + "', '" + key.namespace() + "', '" + key.name()
					+ "', 100, 1, " + now + ", " + now + ")"
			);
			statement.executeUpdate(
				"INSERT INTO region_geometries(region_id, geometry_type, min_x, min_y, min_z, "
					+ "max_x_exclusive, max_y_exclusive, max_z_exclusive) VALUES ('"
					+ id + "', 'CUBOID', 0, 0, 0, 16, 16, 16)"
			);
			statement.executeUpdate(
				"INSERT INTO region_flags(region_id, action, decision) VALUES ('"
					+ id + "', 'BLOCK_BREAK', 'DENY')"
			);
			statement.execute("PRAGMA user_version = 6");
		}
	}
}
