package com.voluble.titanMC.onboarding.preview.actor;

import com.voluble.titanMC.onboarding.preview.OutfitPreview;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class CarouselPreviewStrategy implements PreviewStrategy {
	private static final long SIDE_SPAWN_DELAY_TICKS = 6L;

	private final PreviewActorFactory actors;
	private final Set<PreviewActor> visible = new LinkedHashSet<>();
	private Wheel wheel;
	private Integer focusIndex;

	CarouselPreviewStrategy(PreviewActorFactory actors) {
		this.actors = actors;
	}

	@Override
	public CompletableFuture<Void> show(OutfitPreview.PreviewScene scene) {
		if (wheel == null || focusIndex == null) return initialize(scene);
		int direction = scene.rotationDirection() != 0
			? scene.rotationDirection()
			: direction(focusIndex, scene.selectedIndex(), scene.selectionSize());
		focusIndex = scene.selectedIndex();
		if (direction >= 0) return rotateForward(scene);
		return rotateBackward(scene);
	}

	@Override
	public void remove() {
		for (PreviewActor actor : Set.copyOf(visible)) {
			actor.remove();
		}
		visible.clear();
		wheel = null;
		focusIndex = null;
	}

	private CompletableFuture<Void> initialize(OutfitPreview.PreviewScene scene) {
		focusIndex = scene.selectedIndex();
		PreviewPath path = PreviewPath.from(scene.stage());
		PreviewActor entrance = actors.create(scene.previous());
		PreviewActor focus = actors.create(scene.focus());
		PreviewActor exit = actors.create(scene.next());
		entrance.stageAt(path.entrance());
		focus.stageAt(path.focus());
		exit.stageAt(path.exit());
		visible.add(entrance);
		visible.add(focus);
		visible.add(exit);
		wheel = new Wheel(entrance, focus, exit);
		return CompletableFuture.completedFuture(null);
	}

	private CompletableFuture<Void> rotateForward(OutfitPreview.PreviewScene scene) {
		PreviewPath path = PreviewPath.from(scene.stage());
		PreviewActor oldEntrance = wheel.entrance;
		PreviewActor oldFocus = wheel.focus;
		PreviewActor oldExit = wheel.exit;
		PreviewActor newExit = actors.create(scene.next());
		visible.add(newExit);
		CompletableFuture<Void> oldEntranceMove = oldEntrance.moveToEntrance()
			.whenComplete((ignored, failure) -> visible.remove(oldEntrance));
		CompletableFuture<Void> focusSideMove = oldFocus.moveToEntranceSlot();
		CompletableFuture<Void> focusMove = oldExit.moveToFocus();
		CompletableFuture<Void> spawnMove = newExit.stageAtLater(path.exit(), SIDE_SPAWN_DELAY_TICKS);
		wheel = new Wheel(oldFocus, oldExit, newExit);
		return CompletableFuture.allOf(oldEntranceMove, focusSideMove, focusMove, spawnMove);
	}

	private CompletableFuture<Void> rotateBackward(OutfitPreview.PreviewScene scene) {
		PreviewPath path = PreviewPath.from(scene.stage());
		PreviewActor oldEntrance = wheel.entrance;
		PreviewActor oldFocus = wheel.focus;
		PreviewActor oldExit = wheel.exit;
		PreviewActor newEntrance = actors.create(scene.previous());
		visible.add(newEntrance);
		CompletableFuture<Void> oldExitMove = oldExit.exit()
			.whenComplete((ignored, failure) -> visible.remove(oldExit));
		CompletableFuture<Void> focusSideMove = oldFocus.moveToExitSlot();
		CompletableFuture<Void> focusMove = oldEntrance.moveToFocus();
		CompletableFuture<Void> spawnMove = newEntrance.stageAtLater(path.entrance(), SIDE_SPAWN_DELAY_TICKS);
		wheel = new Wheel(newEntrance, oldEntrance, oldFocus);
		return CompletableFuture.allOf(oldExitMove, focusSideMove, focusMove, spawnMove);
	}

	private int direction(int previous, int next, int size) {
		if (previous == next || size <= 1) return 1;
		if (Math.floorMod(previous + 1, size) == next) return 1;
		if (Math.floorMod(previous - 1, size) == next) return -1;
		int forward = Math.floorMod(next - previous, size);
		int backward = Math.floorMod(previous - next, size);
		return forward <= backward ? 1 : -1;
	}

	private record Wheel(PreviewActor entrance, PreviewActor focus, PreviewActor exit) {
		private Wheel {
			Objects.requireNonNull(entrance, "entrance");
			Objects.requireNonNull(focus, "focus");
			Objects.requireNonNull(exit, "exit");
		}
	}
}
