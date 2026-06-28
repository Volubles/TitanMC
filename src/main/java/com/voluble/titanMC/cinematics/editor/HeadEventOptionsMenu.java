package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import com.voluble.titanMC.util.ChatUtils;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class HeadEventOptionsMenu {
	private final MenuService menus;
	private final CinematicEditorService editor;
	private final CinematicEditorItemFactory items;

	HeadEventOptionsMenu(MenuService menus, CinematicEditorService editor, CinematicEditorItemFactory items) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.editor = Objects.requireNonNull(editor, "editor");
		this.items = Objects.requireNonNull(items, "items");
	}

	void open(Player player, HeadCinematicEvent event) {
		MenuDefinition.chest(3)
			.title(MiniMessage.miniMessage().deserialize("<#f28c28>Head Event <gray>| Slot <white>" + event.timelineSlot()))
			.onOpen(context -> {
				context.setItem(4, CinematicEditorChrome.display(items.selectedEvent(event)));
				context.setItem(10, promptButton(player, event, Material.CARVED_PUMPKIN, "<#f28c28><bold>Set Material", List.of(
					"<gray>Current material: <white>" + event.material(),
					"<gray>Use <white>air</white> to clear the helmet slot.",
					"<green>Click to edit."
				), "Type the Bukkit material, for example carved_pumpkin.", value ->
					new HeadCinematicEvent(event.tick(), event.timelineSlot(), event.row(), normalizeMaterial(value))));
				context.setItem(11, promptButton(player, event, Material.CLOCK, "<#f7d774><bold>Set Tick", CinematicEditorLore.edit("Current tick", CinematicTimeFormat.tickTime(event.tick())), "Type the new tick.", value ->
					new HeadCinematicEvent(CinematicEditorParsing.nonNegativeInt(value), event.timelineSlot(), event.row(), event.material())));
				context.setItem(12, promptButton(player, event, Material.HOPPER, "<#f7d774><bold>Set Row", CinematicEditorLore.edit("Current row", String.valueOf(event.row())), "Type the new row. Row 0 is reserved for cameras.", value ->
					new HeadCinematicEvent(event.tick(), event.timelineSlot(), CinematicEditorParsing.positiveInt(value), event.material())));
				context.setItem(16, button(
					Material.REPEATER,
					"<#f7d774><bold>Move Slot",
					CinematicEditorClickSteps.slotControlLore("this head event"),
					click -> CinematicEditorTimelineMutations.moveEventSlot(
						player,
						editor,
						event,
						CinematicEditorClickSteps.signedSlotDelta(click),
						click.actions()
					)
				));
				context.setItem(17, button(
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
				context.setItem(26, button(Material.REDSTONE_BLOCK, "<#d43030><bold>Delete", List.of("<gray>Remove this head event."), click -> {
					editor.removeEvent(player, event);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
			})
			.build()
			.open(menus, player);
	}

	private io.voluble.michellelib.menu.item.MenuItem promptButton(
		Player player,
		HeadCinematicEvent event,
		Material material,
		String name,
		List<String> lore,
		String prompt,
		Function<String, HeadCinematicEvent> mapper
	) {
		return button(material, name, lore, click -> {
			editor.input().prompt(
				player,
				prompt,
				value -> {
					try {
						HeadCinematicEvent updated = mapper.apply(value);
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

	private String normalizeMaterial(String value) {
		Material material = Material.matchMaterial(value);
		if (material == null) throw new IllegalArgumentException("unknown material");
		return material.key().value();
	}
}
