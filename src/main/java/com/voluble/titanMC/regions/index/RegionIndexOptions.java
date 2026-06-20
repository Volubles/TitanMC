package com.voluble.titanMC.regions.index;

public record RegionIndexOptions(int maxGeometryComplexity, long maxChunksPerRegion, long maxTotalChunkEntries) {

	public RegionIndexOptions {
		if (maxGeometryComplexity <= 0) throw new IllegalArgumentException("maxGeometryComplexity must be positive");
		if (maxChunksPerRegion <= 0) throw new IllegalArgumentException("maxChunksPerRegion must be positive");
		if (maxTotalChunkEntries <= 0) throw new IllegalArgumentException("maxTotalChunkEntries must be positive");
	}

	public static RegionIndexOptions defaults() {
		return new RegionIndexOptions(1_024, 65_536L, 2_000_000L);
	}
}
