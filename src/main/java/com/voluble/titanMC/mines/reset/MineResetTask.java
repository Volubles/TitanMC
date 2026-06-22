package com.voluble.titanMC.mines.reset;

public interface MineResetTask {

	String name();

	int maxBlocksPerSlice();

	MineResetWork process(int maxBlocks, long deadlineNanos);

	void cancel();

	default boolean successful() {
		return true;
	}
}
