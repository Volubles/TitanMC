package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.CommandCinematicEvent;
import com.voluble.titanMC.util.ChatUtils;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class CommandEventOptionsMenu {
	private final MenuService menus;
	private final CinematicEditorService editor;
	private final CinematicEditorItemFactory items;

	CommandEventOptionsMenu(MenuService menus, CinematicEditorService editor, CinematicEditorItemFactory items) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.editor = Objects.requireNonNull(editor, "editor");
		this.items = Objects.requireNonNull(items, "items");
	}

	void open(Player player, CommandCinematicEvent event) {
		MenuDefinition.chest(3)
			.title(MiniMessage.miniMessage().deserialize("<#f7d774>Command Event <gray>| Slot <white>" + event.timelineSlot()))
			.onOpen(context -> {
				context.setItem(4, CinematicEditorChrome.display(items.selectedEvent(event)));
				context.setItem(10, button(Material.PAPER, "<#f7d774><bold>Set Command", List.of(
					"<gray>Current: <white>" + event.command(),
					"<green>Click to type a command."
				), click -> prompt(player, event, "Type the command without a leading slash.", value ->
					new CommandCinematicEvent(event.tick(), event.timelineSlot(), event.row(), value, event.console()))));
				context.setItem(11, button(Material.COMPARATOR, "<#30bbf1><bold>Toggle Sender", List.of(
					"<gray>Current: <white>" + (event.console() ? "Console" : "Player"),
					"<green>Click to toggle."
				), click -> {
					CommandCinematicEvent updated = new CommandCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.command(), !event.console());
					editor.replaceEvent(player, event, updated);
					click.actions().transition(() -> open(player, updated));
				}));
				context.setItem(12, promptButton(player, event, Material.CLOCK, "<#f7d774><bold>Set Tick", CinematicEditorLore.edit("Current tick", CinematicTimeFormat.tickTime(event.tick())), "Type the new tick.", value ->
					new CommandCinematicEvent(CinematicEditorParsing.nonNegativeInt(value), event.timelineSlot(), event.row(), event.command(), event.console())));
				context.setItem(13, promptButton(player, event, Material.HOPPER, "<#f7d774><bold>Set Row", CinematicEditorLore.edit("Current row", String.valueOf(event.row())), "Type the new row. Row 0 is reserved for cameras.", value ->
					new CommandCinematicEvent(event.tick(), event.timelineSlot(), CinematicEditorParsing.positiveInt(value), event.command(), event.console())));
				context.setItem(15, button(Material.REDSTONE_BLOCK, "<#d43030><bold>Delete", List.of("<gray>Remove this command event."), click -> {
					editor.removeEvent(player, event);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
				context.setItem(16, button(
					Material.REPEATER,
					"<#f7d774><bold>Move Slot",
					CinematicEditorClickSteps.slotControlLore("this command event"),
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
			})
			.build()
			.open(menus, player);
	}

	private io.voluble.michellelib.menu.item.MenuItem promptButton(
		Player player,
		CommandCinematicEvent event,
		Material material,
		String name,
		List<String> lore,
		String prompt,
		Function<String, CommandCinematicEvent> mapper
	) {
		return button(material, name, lore, click ->
			prompt(player, event, prompt, mapper));
	}

	private void prompt(
		Player player,
		CommandCinematicEvent event,
		String prompt,
		Function<String, CommandCinematicEvent> mapper
	) {
		editor.input().prompt(
			player,
			prompt,
			value -> {
				try {
					CommandCinematicEvent updated = mapper.apply(value);
					editor.replaceEvent(player, event, updated);
					open(player, updated);
				} catch (IllegalArgumentException exception) {
					player.sendMessage(ChatUtils.format("<#d43030>Invalid value."));
					open(player, event);
				}
			},
			() -> open(player, event)
		);
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
