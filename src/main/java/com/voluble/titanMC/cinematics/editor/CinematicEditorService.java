package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.config.CinematicConfigurationManager;
import com.voluble.titanMC.cinematics.model.CameraPathDefinition;
import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.model.CinematicEvent;
import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.cinematics.model.CinematicTimeline;
import com.voluble.titanMC.cinematics.model.CommandCinematicEvent;
import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import com.voluble.titanMC.cinematics.model.ParticleCinematicEvent;
import com.voluble.titanMC.cinematics.model.ScreenCinematicEvent;
import com.voluble.titanMC.cinematics.model.SoundCinematicEvent;
import com.voluble.titanMC.cinematics.runtime.CinematicRuntime;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import io.voluble.michellelib.menu.MenuService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CinematicEditorService {
	private final CinematicConfigurationManager configuration;
	private final CinematicRuntime runtime;
	private final PluginMessageService messages;
	private final Map<UUID, CinematicEditorSession> sessions = new ConcurrentHashMap<>();
	private final CinematicEditorInputService input;
	private final TimelineMenu timelineMenu;
	private final AddNodeMenu addNodeMenu;
	private final CameraPointOptionsMenu cameraOptions;
	private final CommandEventOptionsMenu commandOptions;
	private final SoundEventOptionsMenu soundOptions;
	private final ParticleEventOptionsMenu particleOptions;
	private final ScreenEventOptionsMenu screenOptions;
	private final HeadEventOptionsMenu headOptions;

	public CinematicEditorService(
		Plugin plugin,
		MenuService menus,
		CinematicConfigurationManager configuration,
		CinematicRuntime runtime,
		PluginMessageService messages
	) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(menus, "menus");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.messages = Objects.requireNonNull(messages, "messages");
		CinematicEditorItemFactory items = new CinematicEditorItemFactory();
		input = new CinematicEditorInputService(plugin);
		timelineMenu = new TimelineMenu(menus, this, items);
		addNodeMenu = new AddNodeMenu(menus, this, items);
		cameraOptions = new CameraPointOptionsMenu(menus, this, items);
		commandOptions = new CommandEventOptionsMenu(menus, this, items);
		soundOptions = new SoundEventOptionsMenu(menus, this, items);
		particleOptions = new ParticleEventOptionsMenu(menus, this, items);
		screenOptions = new ScreenEventOptionsMenu(menus, this, items);
		headOptions = new HeadEventOptionsMenu(menus, this, items);
	}

	public CinematicEditorInputService input() {
		return input;
	}

	public void open(Player player, CinematicId id) {
		configuration.createIfMissing(id, player.getLocation());
		sessions.compute(player.getUniqueId(), (uuid, existing) -> existing == null || !existing.cinematicId().equals(id)
			? new CinematicEditorSession(id)
			: existing);
		timelineMenu.open(player);
	}

	void openTimeline(Player player) {
		timelineMenu.open(player);
	}

	void openAddNode(Player player, int timelineSlot, int row) {
		addNodeMenu.open(player, timelineSlot, row);
	}

	void openCameraOptions(Player player, CameraPoint point) {
		cameraOptions.open(player, point);
	}

	void openEventOptions(Player player, CinematicEvent event) {
		switch (event) {
			case com.voluble.titanMC.cinematics.model.CommandCinematicEvent command -> commandOptions.open(player, command);
			case com.voluble.titanMC.cinematics.model.HeadCinematicEvent head -> headOptions.open(player, head);
			case com.voluble.titanMC.cinematics.model.ParticleCinematicEvent particle -> particleOptions.open(player, particle);
			case com.voluble.titanMC.cinematics.model.ScreenCinematicEvent screen -> screenOptions.open(player, screen);
			case com.voluble.titanMC.cinematics.model.SoundCinematicEvent sound -> soundOptions.open(player, sound);
		}
	}

	CinematicEditorSession session(Player player) {
		CinematicEditorSession session = sessions.get(player.getUniqueId());
		if (session == null) throw new IllegalStateException("player has no cinematic editor session");
		return session;
	}

	Optional<CinematicDefinition> definition(Player player) {
		return configuration.current().find(session(player).cinematicId());
	}

	CameraPoint addCameraPoint(Player player, int timelineSlot) {
		CinematicDefinition definition = requireDefinition(player);
		int tick = defaultTickForSlot(player, timelineSlot);
		CameraPoint point = CameraPoint.at(tick, timelineSlot, player.getLocation());
		List<CameraPoint> points = new ArrayList<>(definition.camera().points().stream()
			.filter(existing -> existing.timelineSlot() != timelineSlot)
			.toList());
		points.add(point);
		save(new CinematicDefinition(
			definition.id(),
			Math.max(definition.durationTicks(), tick),
			new CameraPathDefinition(definition.camera().restorePlayer(), points),
			definition.timeline()
		));
		return point;
	}

	void replaceCameraPoint(Player player, CameraPoint oldPoint, CameraPoint newPoint) {
		CinematicDefinition definition = requireDefinition(player);
		List<CameraPoint> points = new ArrayList<>();
		boolean replaced = false;
		for (CameraPoint point : definition.camera().points()) {
			if (point.equals(oldPoint)) {
				points.add(newPoint);
				replaced = true;
			} else if (point.timelineSlot() != newPoint.timelineSlot()) {
				points.add(point);
			}
		}
		if (!replaced) points.add(newPoint);
		save(new CinematicDefinition(
			definition.id(),
			Math.max(definition.durationTicks(), newPoint.tick()),
			new CameraPathDefinition(definition.camera().restorePlayer(), points),
			definition.timeline()
		));
	}

	Optional<CameraPoint> moveCameraPointSlot(Player player, CameraPoint point, int deltaSlots) {
		int timelineSlot = point.timelineSlot() + deltaSlots;
		if (timelineSlot < 0) return Optional.empty();
		CinematicDefinition definition = requireDefinition(player);
		boolean occupied = definition.camera().points().stream()
			.anyMatch(existing -> !existing.equals(point) && existing.timelineSlot() == timelineSlot);
		if (occupied) return Optional.empty();
		CameraPoint updated = new CameraPoint(point.tick(), timelineSlot, point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
		replaceCameraPoint(player, point, updated);
		return Optional.of(updated);
	}

	boolean removeCameraPoint(Player player, CameraPoint point) {
		CinematicDefinition definition = requireDefinition(player);
		if (definition.camera().points().size() <= 1) return false;
		List<CameraPoint> points = definition.camera().points().stream()
			.filter(existing -> !existing.equals(point))
			.toList();
		save(new CinematicDefinition(
			definition.id(),
			definition.durationTicks(),
			new CameraPathDefinition(definition.camera().restorePlayer(), points),
			definition.timeline()
		));
		return true;
	}

	void addEvent(Player player, CinematicEvent event) {
		CinematicDefinition definition = requireDefinition(player);
		List<CinematicEvent> events = new ArrayList<>(definition.timeline().events().stream()
			.filter(existing -> existing.timelineSlot() != event.timelineSlot() || existing.row() != event.row())
			.toList());
		events.add(event);
		save(new CinematicDefinition(
			definition.id(),
			Math.max(definition.durationTicks(), event.tick()),
			definition.camera(),
			new CinematicTimeline(events)
		));
	}

	void replaceEvent(Player player, CinematicEvent oldEvent, CinematicEvent newEvent) {
		CinematicDefinition definition = requireDefinition(player);
		List<CinematicEvent> events = new ArrayList<>(definition.timeline().events().stream()
			.filter(existing -> existing.equals(oldEvent) || existing.timelineSlot() != newEvent.timelineSlot() || existing.row() != newEvent.row())
			.toList());
		int index = events.indexOf(oldEvent);
		if (index >= 0) {
			events.set(index, newEvent);
		} else {
			events.add(newEvent);
		}
		save(new CinematicDefinition(
			definition.id(),
			Math.max(definition.durationTicks(), newEvent.tick()),
			definition.camera(),
			new CinematicTimeline(events)
		));
	}

	Optional<CinematicEvent> moveEventSlot(Player player, CinematicEvent event, int deltaSlots) {
		int timelineSlot = event.timelineSlot() + deltaSlots;
		if (timelineSlot < 0) return Optional.empty();
		CinematicDefinition definition = requireDefinition(player);
		boolean occupied = definition.timeline().events().stream()
			.anyMatch(existing -> !existing.equals(event) && existing.timelineSlot() == timelineSlot && existing.row() == event.row());
		if (occupied) return Optional.empty();
		CinematicEvent updated = withTimelineSlot(event, timelineSlot);
		replaceEvent(player, event, updated);
		return Optional.of(updated);
	}

	void removeEvent(Player player, CinematicEvent event) {
		CinematicDefinition definition = requireDefinition(player);
		save(new CinematicDefinition(
			definition.id(),
			definition.durationTicks(),
			definition.camera(),
			definition.timeline().without(event)
		));
	}

	void setDuration(Player player, int durationTicks) {
		CinematicDefinition definition = requireDefinition(player);
		save(new CinematicDefinition(definition.id(), durationTicks, definition.camera(), definition.timeline()));
	}

	int defaultTickForSlot(Player player, int timelineSlot) {
		CinematicDefinition definition = requireDefinition(player);
		return definition.camera().points().stream()
			.filter(point -> point.timelineSlot() < timelineSlot)
			.max(java.util.Comparator.comparingInt(CameraPoint::timelineSlot))
			.map(CameraPoint::tick)
			.orElse(0);
	}

	boolean shiftTimeline(Player player, int startSlot, int deltaSlots) {
		if (deltaSlots == 0) return true;
		CinematicDefinition definition = requireDefinition(player);
		List<CameraPoint> points = new ArrayList<>();
		Set<Integer> cameraSlots = new HashSet<>();
		for (CameraPoint point : definition.camera().points()) {
			int timelineSlot = point.timelineSlot() >= startSlot ? point.timelineSlot() + deltaSlots : point.timelineSlot();
			if (timelineSlot < 0 || !cameraSlots.add(timelineSlot)) return false;
			points.add(new CameraPoint(point.tick(), timelineSlot, point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
		}

		List<CinematicEvent> events = new ArrayList<>();
		Set<EventSlot> eventSlots = new HashSet<>();
		for (CinematicEvent event : definition.timeline().events()) {
			int timelineSlot = event.timelineSlot() >= startSlot ? event.timelineSlot() + deltaSlots : event.timelineSlot();
			EventSlot slot = new EventSlot(timelineSlot, event.row());
			if (timelineSlot < 0 || !eventSlots.add(slot)) return false;
			events.add(withTimelineSlot(event, timelineSlot));
		}

		int length = Math.max(definition.durationTicks(), maxTick(points, events));
		save(new CinematicDefinition(
			definition.id(),
			Math.max(1, length),
			new CameraPathDefinition(definition.camera().restorePlayer(), points),
			new CinematicTimeline(events)
		));
		return true;
	}

	void preview(Player player) {
		CinematicId id = session(player).cinematicId();
		CinematicRuntime.StartResult result = runtime.start(player, id);
		switch (result) {
			case STARTED -> messages.send(player, MessageDefaults.CINEMATICS_STARTED, args -> args.plain("cinematic", id.value()));
			case DISABLED -> messages.send(player, MessageDefaults.CINEMATICS_DISABLED);
			case UNKNOWN -> messages.send(player, MessageDefaults.CINEMATICS_UNKNOWN, args -> args.plain("cinematic", id.value()));
		}
	}

	private int maxTick(List<CameraPoint> points, List<CinematicEvent> events) {
		int tick = 1;
		for (CameraPoint point : points) {
			tick = Math.max(tick, point.tick());
		}
		for (CinematicEvent event : events) {
			tick = Math.max(tick, event.tick());
		}
		return tick;
	}

	private CinematicEvent withTick(CinematicEvent event, int tick) {
		return switch (event) {
			case CommandCinematicEvent command -> new CommandCinematicEvent(tick, command.timelineSlot(), command.row(), command.command(), command.console());
			case HeadCinematicEvent head -> new HeadCinematicEvent(tick, head.timelineSlot(), head.row(), head.material());
			case ParticleCinematicEvent particle -> new ParticleCinematicEvent(
				tick,
				particle.timelineSlot(),
				particle.row(),
				particle.position(),
				particle.particle(),
				particle.count(),
				particle.offsetX(),
				particle.offsetY(),
				particle.offsetZ(),
				particle.speed()
			);
			case SoundCinematicEvent sound -> new SoundCinematicEvent(
				tick,
				sound.timelineSlot(),
				sound.row(),
				sound.position(),
				sound.key(),
				sound.volume(),
				sound.pitch(),
				sound.category()
			);
			case ScreenCinematicEvent screen -> new ScreenCinematicEvent(
				tick,
				screen.timelineSlot(),
				screen.row(),
				screen.screenId(),
				screen.title(),
				screen.timing()
			);
		};
	}

	private CinematicEvent withTimelineSlot(CinematicEvent event, int timelineSlot) {
		return switch (event) {
			case CommandCinematicEvent command -> new CommandCinematicEvent(command.tick(), timelineSlot, command.row(), command.command(), command.console());
			case HeadCinematicEvent head -> new HeadCinematicEvent(head.tick(), timelineSlot, head.row(), head.material());
			case ParticleCinematicEvent particle -> new ParticleCinematicEvent(
				particle.tick(),
				timelineSlot,
				particle.row(),
				particle.position(),
				particle.particle(),
				particle.count(),
				particle.offsetX(),
				particle.offsetY(),
				particle.offsetZ(),
				particle.speed()
			);
			case SoundCinematicEvent sound -> new SoundCinematicEvent(
				sound.tick(),
				timelineSlot,
				sound.row(),
				sound.position(),
				sound.key(),
				sound.volume(),
				sound.pitch(),
				sound.category()
			);
			case ScreenCinematicEvent screen -> new ScreenCinematicEvent(
				screen.tick(),
				timelineSlot,
				screen.row(),
				screen.screenId(),
				screen.title(),
				screen.timing()
			);
		};
	}

	private CinematicDefinition requireDefinition(Player player) {
		return definition(player).orElseThrow(() -> new IllegalStateException("Unknown cinematic: " + session(player).cinematicId().value()));
	}

	private void save(CinematicDefinition definition) {
		configuration.saveDefinition(definition);
	}

	private record EventSlot(int tick, int row) {
	}
}
