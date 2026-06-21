package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.TrackedCellBlock;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.util.RegionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CellStorageTest {
	@TempDir Path directory;
	@Test void cellsLeasesAndTrackedBlocksSurviveRestart() throws Exception {
		Path database=directory.resolve("cells.db"); UUID world=UUID.randomUUID(); UUID owner=UUID.randomUUID();
		CellDefinition cell=new CellDefinition("a1",new RegionUtils.Cuboid(world,0,0,0,5,5,5),500,86400,true);
		CellLease lease=new CellLease("a1",owner,1,1000,2000,true);
		TrackedCellBlock block=new TrackedCellBlock("a1",1,world,2,2,2);
		try(CellStorage storage=new CellStorage(database)){storage.saveCell(cell).join();storage.saveLease(lease).join();storage.addBlocks(List.of(block)).join();}
		try(CellStorage storage=new CellStorage(database)){assertEquals(cell,storage.loadCells().get("a1"));assertEquals(lease,storage.loadLeases().get("a1"));assertEquals(List.of(block),storage.loadBlocks());}
	}
}
