package com.voluble.titanMC.mines.reset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineResetQueueTest {

	@Test
	void rotatesFairlyWhenTheFirstTaskConsumesTheTickBudget() {
		AtomicLong clock = new AtomicLong();
		List<String> visits = new ArrayList<>();
		MineResetQueue queue = new MineResetQueue(clock::get);
		queue.replace(task("first", visits, clock, 10));
		queue.replace(task("second", visits, clock, 10));

		queue.processTick(5, ignored -> {});
		queue.processTick(5, ignored -> {});

		assertEquals(List.of("first", "second"), visits);
	}

	@Test
	void removesCompletedTasksAndReportsCompletion() {
		AtomicLong clock = new AtomicLong();
		MineResetQueue queue = new MineResetQueue(clock::get);
		List<String> completed = new ArrayList<>();
		queue.replace(new MineResetTask() {
			@Override public String name() { return "done"; }
			@Override public int maxBlocksPerSlice() { return 100; }
			@Override public MineResetWork process(int maxBlocks, long deadlineNanos) {
				return new MineResetWork(1, 1, true);
			}
			@Override public void cancel() {}
		});

		MineResetTick tick = queue.processTick(100, completed::add);

		assertEquals(List.of("done"), completed);
		assertEquals(0, queue.size());
		assertEquals(new MineResetTick(1, 1, 1), tick);
	}

	@Test
	void failedTasksAreRemovedWithoutReportingSuccessfulCompletion() {
		MineResetQueue queue = new MineResetQueue();
		List<String> completed = new ArrayList<>();
		queue.replace(new MineResetTask() {
			@Override public String name() { return "failed"; }
			@Override public int maxBlocksPerSlice() { return 1; }
			@Override public MineResetWork process(int maxBlocks, long deadlineNanos) {
				return new MineResetWork(0, 0, true);
			}
			@Override public void cancel() {}
			@Override public boolean successful() { return false; }
		});

		MineResetTick tick = queue.processTick(100, completed::add);

		assertTrue(completed.isEmpty());
		assertEquals(0, queue.size());
		assertEquals(0, tick.completedResets());
	}

	private static MineResetTask task(
		String name,
		List<String> visits,
		AtomicLong clock,
		long cost
	) {
		return new MineResetTask() {
			@Override public String name() { return name; }
			@Override public int maxBlocksPerSlice() { return 100; }
			@Override public MineResetWork process(int maxBlocks, long deadlineNanos) {
				visits.add(name);
				clock.addAndGet(cost);
				return new MineResetWork(1, 1, false);
			}
			@Override public void cancel() {}
		};
	}
}
