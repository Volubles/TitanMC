package com.voluble.titanMC.auctions;

import com.voluble.titanMC.cells.model.CellRecoveryLot;
import com.voluble.titanMC.ranks.model.WardId;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionStorageTest {
	@TempDir
	Path directory;

	@Test
	void positionsAndQueuedBatchesSurviveRestart() throws Exception {
		Path database = directory.resolve("auctions.db");
		AuctionPosition position = new AuctionPosition("north_1", WardId.of("d"), UUID.randomUUID(), 1, 64, 2, BlockFace.SOUTH);
		List<byte[]> items = new ArrayList<>();
		for (int index = 0; index < 30; index++) items.add(new byte[]{(byte) index});

		try (AuctionStorage storage = new AuctionStorage(database)) {
			storage.savePosition(position);
			assertTrue(storage.ingest(new CellRecoveryLot(42, UUID.randomUUID(), WardId.of("d"), items), () -> 1200));
			assertFalse(storage.ingest(new CellRecoveryLot(42, UUID.randomUUID(), WardId.of("d"), items), () -> 9999));
		}

		try (AuctionStorage storage = new AuctionStorage(database)) {
			assertEquals(position, storage.loadPositions().get("north_1"));
			List<AuctionLot> auctions = storage.loadAuctions();
			assertEquals(2, auctions.size());
			assertEquals(27, auctions.get(0).items().size());
			assertEquals(3, auctions.get(1).items().size());
			assertEquals(1200, auctions.get(0).price());
			assertEquals(WardId.of("d"), auctions.get(0).wardId());
			assertEquals(AuctionState.QUEUED, auctions.get(0).state());
		}
	}

	@Test
	void deletingAuctionPermanentlyRemovesItsItems() throws Exception {
		Path database = directory.resolve("delete-auction.db");
		try (AuctionStorage storage = new AuctionStorage(database)) {
			storage.ingest(
				new CellRecoveryLot(91, UUID.randomUUID(), WardId.of("e"), List.of(new byte[]{4, 5, 6})),
				() -> 800
			);
			long auctionId = storage.loadAuctions().getFirst().id();

			storage.deleteAuction(auctionId);

			assertTrue(storage.loadAuctions().isEmpty());
		}
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
			 var statement = connection.createStatement();
			 var result = statement.executeQuery("SELECT COUNT(*) FROM auction_items")) {
			assertTrue(result.next());
			assertEquals(0, result.getInt(1));
		}
	}

	@Test
	void stateAndRemainingItemsAreDurable() throws Exception {
		Path database = directory.resolve("state.db");
		AuctionPosition position = new AuctionPosition("slot", WardId.of("e"), UUID.randomUUID(), 3, 70, 4, BlockFace.NORTH);
		try (AuctionStorage storage = new AuctionStorage(database)) {
			storage.savePosition(position);
			storage.ingest(new CellRecoveryLot(7, UUID.randomUUID(), WardId.of("e"), List.of(new byte[]{1}, new byte[]{2})), () -> 500);
			AuctionLot queued = storage.loadAuctions().getFirst();
			AuctionLot forSale = queued.atPosition(position.id(), 12345);
			storage.saveAuction(forSale);
			storage.replaceItems(forSale.id(), List.of(new byte[]{2}));
		}

		try (AuctionStorage storage = new AuctionStorage(database)) {
			AuctionLot loaded = storage.loadAuctions().getFirst();
			assertEquals(AuctionState.FOR_SALE, loaded.state());
			assertEquals("slot", loaded.positionId());
			assertEquals(12345, loaded.saleExpiresAt());
			assertEquals(1, loaded.items().size());
		}
	}

	@Test
	void legacyAuctionsMigrateToEWard() throws Exception {
		Path database = directory.resolve("legacy.db");
		UUID world = UUID.randomUUID();
		Class.forName("org.sqlite.JDBC");
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 var statement = connection.createStatement()) {
			statement.executeUpdate("""
				CREATE TABLE auction_positions (
				 id TEXT PRIMARY KEY, world_id TEXT NOT NULL,
				 x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
				 facing TEXT NOT NULL, UNIQUE(world_id,x,y,z)
				)
				""");
			statement.executeUpdate("INSERT INTO auction_positions VALUES('legacy','" + world + "',1,64,2,'NORTH')");
			statement.executeUpdate("""
				CREATE TABLE auctions (
				 id INTEGER PRIMARY KEY AUTOINCREMENT, source_lot_id INTEGER NOT NULL,
				 batch_index INTEGER NOT NULL, position_id TEXT, price INTEGER NOT NULL,
				 state TEXT NOT NULL, buyer_id TEXT, buyer_name TEXT,
				 sale_expires_at INTEGER NOT NULL DEFAULT 0,
				 claim_expires_at INTEGER NOT NULL DEFAULT 0,
				 UNIQUE(source_lot_id,batch_index)
				)
				""");
			statement.executeUpdate("INSERT INTO auctions(source_lot_id,batch_index,position_id,price,state) VALUES(7,0,'legacy',500,'FOR_SALE')");
		}

		try (AuctionStorage storage = new AuctionStorage(database)) {
			assertEquals(WardId.of("e"), storage.loadPositions().get("legacy").wardId());
			assertEquals(WardId.of("e"), storage.loadAuctions().getFirst().wardId());
		}
	}
}
