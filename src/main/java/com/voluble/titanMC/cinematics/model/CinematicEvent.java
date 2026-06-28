package com.voluble.titanMC.cinematics.model;

public sealed interface CinematicEvent permits CommandCinematicEvent, HeadCinematicEvent, ParticleCinematicEvent, ScreenCinematicEvent, SoundCinematicEvent {
	int tick();

	int timelineSlot();

	int row();

	CinematicEventType type();
}
