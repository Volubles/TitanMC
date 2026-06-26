package com.voluble.titanMC.milestones.ui;

import java.util.ArrayList;
import java.util.List;

public final class MilestoneMenuLayout {
	public static final int SUMMARY = 4;
	public static final List<Integer> CATEGORY_SLOTS = List.of(11, 13, 15, 21, 23);
	public static final List<Integer> TRACK_SLOTS = List.of(
		10, 11, 12, 13, 14, 15, 16,
		19, 20, 21, 22, 23, 24, 25
	);
	public static final List<Integer> TIER_SLOTS = List.of(
		10, 11, 12, 13, 14, 15, 16,
		19, 20, 21, 22, 23, 24, 25
	);

	private MilestoneMenuLayout() {
	}

	public static List<Integer> footerSlots(int rows) {
		int start = footerStart(rows);
		List<Integer> slots = new ArrayList<>(9);
		for (int offset = 0; offset < 9; offset++) {
			slots.add(start + offset);
		}
		return slots;
	}

	public static int previousSlot(int rows) {
		return footerStart(rows);
	}

	public static int centerFooterSlot(int rows) {
		return footerStart(rows) + 4;
	}

	public static int nextSlot(int rows) {
		return footerStart(rows) + 8;
	}

	public static List<Integer> centeredSlots(List<Integer> slots, int itemCount) {
		List<List<Integer>> rows = rows(slots);
		int remaining = Math.min(itemCount, slots.size());
		int rowsUsed = rowsUsed(rows, remaining);
		List<Integer> centered = new ArrayList<>(remaining);
		for (int rowIndex = 0; rowIndex < rowsUsed && remaining > 0; rowIndex++) {
			List<Integer> rowSlots = rows.get(rowIndex);
			int count = Math.min(rowSlots.size(), (int) Math.ceil((double) remaining / (rowsUsed - rowIndex)));
			int start = (rowSlots.size() - count) / 2;
			centered.addAll(rowSlots.subList(start, start + count));
			remaining -= count;
		}
		return centered;
	}

	private static List<List<Integer>> rows(List<Integer> slots) {
		List<List<Integer>> rows = new ArrayList<>();
		for (int index = 0; index < slots.size();) {
			int row = slots.get(index) / 9;
			List<Integer> rowSlots = new ArrayList<>();
			while (index < slots.size() && slots.get(index) / 9 == row) {
				rowSlots.add(slots.get(index));
				index++;
			}
			rows.add(rowSlots);
		}
		return rows;
	}

	private static int rowsUsed(List<List<Integer>> rows, int itemCount) {
		int remaining = Math.max(0, itemCount);
		for (int index = 0; index < rows.size(); index++) {
			remaining -= rows.get(index).size();
			if (remaining <= 0) return index + 1;
		}
		return rows.size();
	}

	private static int footerStart(int rows) {
		return (Math.max(1, rows) - 1) * 9;
	}
}
