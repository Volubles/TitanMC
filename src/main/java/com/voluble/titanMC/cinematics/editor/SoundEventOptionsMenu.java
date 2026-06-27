package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.CinematicEventPosition;
import com.voluble.titanMC.cinematics.model.SoundCinematicEvent;
import com.voluble.titanMC.util.ChatUtils;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class SoundEventOptionsMenu {
	private final MenuService menus;
	private final CinematicEditorService editor;
	private final CinematicEditorItemFactory items;

	SoundEventOptionsMenu(MenuService menus, CinematicEditorService editor, CinematicEditorItemFactory items) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.editor = Objects.requireNonNull(editor, "editor");
		this.items = Objects.requireNonNull(items, "items");
	}

	void open(Player player, SoundCinematicEvent event) {
		MenuDefinition.chest(3)
			.title(MiniMessage.miniMessage().deserialize("<#42d829>Sound Event <gray>| Slot <white>" + event.timelineSlot()))
			.onOpen(context -> {
				context.setItem(4, CinematicEditorChrome.display(items.selectedEvent(event)));
				context.setItem(10, promptButton(player, event, Material.NOTE_BLOCK, "<#42d829><bold>Set Sound Key", CinematicEditorLore.edit("Current sound", event.key()), "Type the sound key.", value ->
					new SoundCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), value, event.volume(), event.pitch(), event.category())));
				context.setItem(11, promptButton(player, event, Material.AMETHYST_SHARD, "<#f7d774><bold>Set Volume", CinematicEditorLore.edit("Current volume", String.valueOf(event.volume())), "Type the volume.", value ->
					new SoundCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.key(), CinematicEditorParsing.nonNegativeFloat(value), event.pitch(), event.category())));
				context.setItem(12, promptButton(player, event, Material.GOAT_HORN, "<#f7d774><bold>Set Pitch", CinematicEditorLore.edit("Current pitch", String.valueOf(event.pitch())), "Type the pitch.", value ->
					new SoundCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.key(), event.volume(), CinematicEditorParsing.nonNegativeFloat(value), event.category())));
				context.setItem(13, promptButton(player, event, Material.SCULK_SENSOR, "<#30bbf1><bold>Set Category", CinematicEditorLore.edit("Current category", event.category()), "Type the Bukkit sound category, for example MASTER or PLAYERS.", value ->
					new SoundCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.key(), event.volume(), event.pitch(), value)));
				context.setItem(14, button(Material.ENDER_EYE, "<#42d829><bold>Capture Location", CinematicEditorLore.captureLocation(event.position()), click -> {
					SoundCinematicEvent updated = new SoundCinematicEvent(
						event.tick(), event.timelineSlot(), event.row(), CinematicEventPosition.at(player.getLocation()), event.key(), event.volume(), event.pitch(), event.category()
					);
					editor.replaceEvent(player, event, updated);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
				context.setItem(15, promptButton(player, event, Material.CLOCK, "<#f7d774><bold>Set Tick", CinematicEditorLore.edit("Current tick", CinematicTimeFormat.tickTime(event.tick())), "Type the new tick.", value ->
					new SoundCinematicEvent(CinematicEditorParsing.nonNegativeInt(value), event.timelineSlot(), event.row(), event.position(), event.key(), event.volume(), event.pitch(), event.category())));
				context.setItem(16, promptButton(player, event, Material.HOPPER, "<#f7d774><bold>Set Row", CinematicEditorLore.edit("Current row", String.valueOf(event.row())), "Type the new row. Row 0 is reserved for cameras.", value ->
					new SoundCinematicEvent(event.tick(), event.timelineSlot(), CinematicEditorParsing.positiveInt(value), event.position(), event.key(), event.volume(), event.pitch(), event.category())));
				context.setItem(18, button(
					Material.REPEATER,
					"<#f7d774><bold>Move Slot",
					CinematicEditorClickSteps.slotControlLore("this sound event"),
					click -> CinematicEditorTimelineMutations.moveEventSlot(
						player,
						editor,
						event,
						CinematicEditorClickSteps.signedSlotDelta(click),
						click.actions()
					)
				));
				context.setItem(19, button(
					Material.PISTON,
					"<#30bbf1><bold>Shift Timeline From Here",
					CinematicEditorClickSteps.slotControlLore("this event and everything after it"),
					click -> CinematicEditorTimelineMutations.shiftTimeline(
						player,
						editor,
						event.timelineSlot(),
						CinematicEditorClickSteps.signedSlotDelta(click),
						click.actions()
					)
				));
				context.setItem(22, button(Material.ARROW, "<#30bbf1><bold>Back", List.of("<gray>Return to the timeline."), click ->
					click.actions().transition(() -> editor.openTimeline(player))));
				context.setItem(26, button(Material.REDSTONE_BLOCK, "<#d43030><bold>Delete", List.of("<gray>Remove this sound event."), click -> {
					editor.removeEvent(player, event);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
			})
			.build()
			.open(menus, player);
	}

	private io.voluble.michellelib.menu.item.MenuItem promptButton(
		Player player,
		SoundCinematicEvent event,
		Material material,
		String name,
		List<String> lore,
		String prompt,
		Function<String, SoundCinematicEvent> mapper
	) {
		return button(material, name, lore, click -> {
			editor.input().prompt(
				player,
				prompt,
				value -> {
					try {
						SoundCinematicEvent updated = mapper.apply(value);
						editor.replaceEvent(player, event, updated);
						open(player, updated);
					} catch (IllegalArgumentException exception) {
						player.sendMessage(ChatUtils.format("<#d43030>Invalid value."));
						open(player, event);
					}
				},
				() -> open(player, event)
			);
		});
	}

	private io.voluble.michellelib.menu.item.MenuItem button(
		Material material,
		String name,
		List<String> lore,
		java.util.function.Consumer<io.voluble.michellelib.menu.item.ClickContext> click
	) {
		return CinematicEditorChrome.button(items, material, name, lore, context -> {
			context.actions().close();
			context.actions().nextTick(() -> click.accept(context));
		});
	}
}
