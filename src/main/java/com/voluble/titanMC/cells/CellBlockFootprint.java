package com.voluble.titanMC.cells;

import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CellBlockFootprint {
	private CellBlockFootprint() {}
	public static List<Block> collect(Block origin) {
		Set<Block> blocks = new LinkedHashSet<>(); blocks.add(origin);
		if (origin.getBlockData() instanceof Bed bed) {
			var d = bed.getFacing().getDirection(); int sign = bed.getPart() == Bed.Part.FOOT ? 1 : -1;
			addSame(blocks, origin, origin.getRelative(sign*d.getBlockX(), sign*d.getBlockY(), sign*d.getBlockZ()));
		}
		if (origin.getBlockData() instanceof Bisected b) addSame(blocks, origin, origin.getRelative(0, b.getHalf()==Bisected.Half.BOTTOM ? 1 : -1, 0));
		return List.copyOf(blocks);
	}
	private static void addSame(Set<Block> blocks, Block origin, Block candidate) { if (candidate.getType()==origin.getType()) blocks.add(candidate); }
}
