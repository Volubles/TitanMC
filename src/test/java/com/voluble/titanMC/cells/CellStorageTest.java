package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.TrackedCellBlock;
import com.voluble.titanMC.cells.model.CellSign;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.util.RegionUtils;
import com.voluble.titanMC.ranks.model.WardId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CellStorageTest {
	@TempDir Path directory;
	@Test void cellsLeasesAndTrackedBlocksSurviveRestart() throws Exception {
		Path database=directory.resolve("cells.db"); UUID world=UUID.randomUUID(); UUID owner=UUID.randomUUID();
		CellDefinition cell=new CellDefinition("a1",WardId.of("e"),new RegionUtils.Cuboid(world,0,0,0,5,5,5),500,86400,604800,true);
		CellLease lease=new CellLease("a1",owner,1,1000,2000);
		TrackedCellBlock block=new TrackedCellBlock("a1",1,world,2,2,2);
		UUID member=UUID.randomUUID();
		CellSign sign=new CellSign("a1",world,10,64,10);
		try(CellStorage storage=new CellStorage(database)){storage.saveCell(cell).join();storage.saveLease(lease).join();storage.addMember("a1",1,member).join();storage.addBlocks(List.of(block)).join();storage.saveSign(sign).join();}
		try(CellStorage storage=new CellStorage(database)){assertEquals(cell,storage.loadCells().get("a1"));var leases=storage.loadLeases();assertEquals(lease,leases.get("a1"));assertEquals(java.util.Set.of(member),storage.loadMembers(leases).get("a1"));assertEquals(List.of(block),storage.loadBlocks());assertEquals(List.of(sign),storage.loadSigns());}
	}
	@Test void resetPreparationIsDurableAndCompletionIsAtomic() throws Exception {
		Path database=directory.resolve("reset.db");UUID world=UUID.randomUUID();UUID owner=UUID.randomUUID();CellDefinition cell=new CellDefinition("a1",WardId.of("e"),new RegionUtils.Cuboid(world,0,0,0,5,5,5),500,86400,604800,true);CellLease lease=new CellLease("a1",owner,3,1000,2000);TrackedCellBlock block=new TrackedCellBlock("a1",3,world,2,2,2);long lot;
		try(CellStorage storage=new CellStorage(database)){storage.saveCell(cell).join();storage.saveLease(lease).join();storage.addBlocks(List.of(block)).join();storage.beginReset(lease).join();lot=storage.createRecoveryLot(lease,List.of(new byte[]{1,2,3})).join();assertEquals(com.voluble.titanMC.cells.model.CellResetJob.Phase.PREPARED,storage.loadResetJobs().get("a1").phase());}
		try(CellStorage storage=new CellStorage(database)){storage.completeReset("a1",3,lot).join();assertEquals(0,storage.loadLeases().size());assertEquals(0,storage.loadBlocks().size());assertEquals(0,storage.loadResetJobs().size());}
	}
	@Test void readyRecoveryLotsCanBeClaimedByAuctions() throws Exception {
		Path database = directory.resolve("recovery.db");
		UUID world = UUID.randomUUID();
		UUID owner = UUID.randomUUID();
		CellDefinition cell = new CellDefinition("a1", WardId.of("e"), new RegionUtils.Cuboid(world, 0, 0, 0, 5, 5, 5), 500, 86400, 604800, true);
		CellLease lease = new CellLease("a1", owner, 1, 1000, 2000);
		try (CellStorage storage = new CellStorage(database)) {
			storage.saveCell(cell).join();
			storage.saveLease(lease).join();
			storage.beginReset(lease).join();
			long lotId = storage.createRecoveryLot(lease, List.of(new byte[]{1})).join();
			storage.completeReset("a1", 1, lotId).join();
			var lots = storage.loadReadyRecoveryLots().join();
			assertEquals(1, lots.size());
			assertEquals(owner, lots.getFirst().ownerId());
			storage.markRecoveryLotAuctioned(lotId).join();
			assertEquals(0, storage.loadReadyRecoveryLots().join().size());
		}
	}
	@Test void schemaOneCellsReceiveDisplayNames() throws Exception {
		Path database=directory.resolve("v1.db");UUID world=UUID.randomUUID();Class.forName("org.sqlite.JDBC");
		try(var connection=DriverManager.getConnection("jdbc:sqlite:"+database);var statement=connection.createStatement()){statement.executeUpdate("CREATE TABLE cells(id TEXT PRIMARY KEY,world_id TEXT NOT NULL,min_x INTEGER NOT NULL,min_y INTEGER NOT NULL,min_z INTEGER NOT NULL,max_x INTEGER NOT NULL,max_y INTEGER NOT NULL,max_z INTEGER NOT NULL,rent_price INTEGER NOT NULL,rent_duration_seconds INTEGER NOT NULL,enabled INTEGER NOT NULL)");statement.executeUpdate("INSERT INTO cells VALUES('cell_01','"+world+"',0,0,0,5,5,5,500,86400,1)");statement.execute("PRAGMA user_version=1");}
		try(CellStorage storage=new CellStorage(database)){
			CellDefinition cell = storage.loadCells().get("cell_01");
			assertEquals("cell_01", cell.displayName());
			assertEquals(86400L * 30L, cell.maxRentDurationSeconds());
		}
	}
}
