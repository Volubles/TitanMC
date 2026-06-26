package com.voluble.titanMC.progression.model;

public interface LevelCurve {
	/**
	 * Total cred required to first reach the given level.
	 * Level 1 always requires 0 cred.
	 */
	long credForLevel(int level);

	/**
	 * The highest level a player has reached given their total cred.
	 * Returns at least 1.
	 */
	int levelAt(long totalCred);
}
