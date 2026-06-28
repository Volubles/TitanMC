package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicEventPosition;
import com.voluble.titanMC.cinematics.model.CommandCinematicEvent;
import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import com.voluble.titanMC.cinematics.model.ParticleCinematicEvent;
import com.voluble.titanMC.cinematics.model.ScreenCinematicEvent;
import com.voluble.titanMC.cinematics.model.SoundCinematicEvent;
import com.voluble.titanMC.display.screen.ScreenEffectId;
import com.voluble.titanMC.display.screen.ScreenEffectTiming;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class AddNodeMenu {
	private final MenuService menus;
	private final CinematicEditorService editor;
	private final CinematicEditorItemFactory items;

	AddNodeMenu(MenuService menus, CinematicEditorService editor, CinematicEditorItemFactory items) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.editor = Objects.requireNonNull(editor, "editor");
		this.items = Objects.requireNonNull(items, "items");
	}

	void open(Player player, int timelineSlot, int row) {
		MenuDefinition.chest(3)
			.title(MiniMessage.miniMessage().deserialize("<#30bbf1>Add Node <gray>| Slot <white>" + timelineSlot))
			.onOpen(context -> {
				if (row == 0) {
					context.setItem(13, CinematicEditorChrome.button(
						items,
						Material.ENDER_EYE,
						"<#30bbf1><bold>Add Camera Point",
						List.of(
							"<gray>Canvas slot: <white>" + timelineSlot,
							"<gray>Default tick: <white>" + CinematicTimeFormat.tickTime(editor.defaultTickForSlot(player, timelineSlot)),
							"",
							"<gray>Captures your current position.",
							"<green>Click to add."
						),
						click -> {
							CameraPoint point = editor.addCameraPoint(player, timelineSlot);
							click.actions().transition(() -> editor.openCameraOptions(player, point));
						}
					));
				} else {
					context.setItem(10, sound(player, timelineSlot, row));
					context.setItem(11, command(player, timelineSlot, row));
					context.setItem(14, screen(player, timelineSlot, row));
					context.setItem(15, head(player, timelineSlot, row));
					context.setItem(16, particle(player, timelineSlot, row));
				}
				context.setItem(22, CinematicEditorChrome.button(
					items,
					Material.ARROW,
					"<#30bbf1><bold>Back",
					List.of("<gray>Return to the timeline."),
					click -> click.actions().transition(() -> editor.openTimeline(player))
				));
			})
			.build()
			.open(menus, player);
	}

	private io.voluble.michellelib.menu.item.MenuItem sound(Player player, int timelineSlot, int row) {
		return CinematicEditorChrome.button(
			items,
			Material.NOTE_BLOCK,
			"<#42d829><bold>Add Sound",
			addLore(player, timelineSlot, "Creates a sound event at your current position."),
			context -> {
				SoundCinematicEvent event = new SoundCinematicEvent(
					editor.defaultTickForSlot(player, timelineSlot),
					timelineSlot,
					row,
					CinematicEventPosition.at(player.getLocation()),
					"minecraft:block.note_block.pling",
					1.0f,
					1.0f,
					"MASTER"
				);
				editor.addEvent(player, event);
				context.actions().transition(() -> editor.openEventOptions(player, event));
			}
		);
	}

	private io.voluble.michellelib.menu.item.MenuItem command(Player player, int timelineSlot, int row) {
		return CinematicEditorChrome.button(
			items,
			Material.COMMAND_BLOCK,
			"<#f7d774><bold>Add Command",
			addLore(player, timelineSlot, "Creates a console command event."),
			context -> {
				CommandCinematicEvent event = new CommandCinematicEvent(
					editor.defaultTickForSlot(player, timelineSlot),
					timelineSlot,
					row,
					"tell {player} Cinematic command event",
					true
				);
				editor.addEvent(player, event);
				context.actions().transition(() -> editor.openEventOptions(player, event));
			}
		);
	}

	private io.voluble.michellelib.menu.item.MenuItem particle(Player player, int timelineSlot, int row) {
		return CinematicEditorChrome.button(
			items,
			Material.BLAZE_POWDER,
			"<#b36bff><bold>Add Particle",
			addLore(player, timelineSlot, "Creates a particle event at your current position."),
			context -> {
				ParticleCinematicEvent event = new ParticleCinematicEvent(
					editor.defaultTickForSlot(player, timelineSlot),
					timelineSlot,
					row,
					CinematicEventPosition.at(player.getLocation()),
					"CLOUD",
					8,
					0.0,
					0.0,
					0.0,
					0.0
				);
				editor.addEvent(player, event);
				context.actions().transition(() -> editor.openEventOptions(player, event));
			}
		);
	}

	private io.voluble.michellelib.menu.item.MenuItem head(Player player, int timelineSlot, int row) {
		return CinematicEditorChrome.button(
			items,
			Material.CARVED_PUMPKIN,
			"<#f28c28><bold>Add Head Item",
			addLore(player, timelineSlot, "Places an item on the player's head."),
			context -> {
				HeadCinematicEvent event = new HeadCinematicEvent(
					editor.defaultTickForSlot(player, timelineSlot),
					timelineSlot,
					row,
					"carved_pumpkin"
				);
				editor.addEvent(player, event);
				context.actions().transition(() -> editor.openEventOptions(player, event));
			}
		);
	}

	private io.voluble.michellelib.menu.item.MenuItem screen(Player player, int timelineSlot, int row) {
		return CinematicEditorChrome.button(
			items,
			Material.BLACK_DYE,
			"<#d43030><bold>Add Screen Fade",
			addLore(player, timelineSlot, "Shows a configured fullscreen screen effect."),
			context -> {
				ScreenCinematicEvent event = new ScreenCinematicEvent(
					editor.defaultTickForSlot(player, timelineSlot),
					timelineSlot,
					row,
					ScreenEffectId.of("fullscreen_black"),
					Optional.empty(),
					Optional.of(new ScreenEffectTiming(10L, 20L, 10L))
				);
				editor.addEvent(player, event);
				context.actions().transition(() -> editor.openEventOptions(player, event));
			}
		);
	}

	private List<String> addLore(Player player, int timelineSlot, String description) {
		return List.of(
			"<gray>Canvas slot: <white>" + timelineSlot,
			"<gray>Default tick: <white>" + CinematicTimeFormat.tickTime(editor.defaultTickForSlot(player, timelineSlot)),
			"",
			"<gray>" + description,
			"<green>Click to add."
		);
	}
}
