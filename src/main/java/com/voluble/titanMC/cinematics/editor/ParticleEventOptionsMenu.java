package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.CinematicEventPosition;
import com.voluble.titanMC.cinematics.model.ParticleCinematicEvent;
import com.voluble.titanMC.util.ChatUtils;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class ParticleEventOptionsMenu {
	private final MenuService menus;
	private final CinematicEditorService editor;
	private final CinematicEditorItemFactory items;

	ParticleEventOptionsMenu(MenuService menus, CinematicEditorService editor, CinematicEditorItemFactory items) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.editor = Objects.requireNonNull(editor, "editor");
		this.items = Objects.requireNonNull(items, "items");
	}

	void open(Player player, ParticleCinematicEvent event) {
		MenuDefinition.chest(3)
			.title(MiniMessage.miniMessage().deserialize("<#b36bff>Particle Event <gray>| Slot <white>" + event.timelineSlot()))
			.onOpen(context -> {
				context.setItem(4, CinematicEditorChrome.display(items.selectedEvent(event)));
				context.setItem(10, promptButton(player, event, Material.BLAZE_POWDER, "<#b36bff><bold>Set Particle", CinematicEditorLore.edit("Current particle", event.particle()), "Type the Bukkit particle name.", value ->
					new ParticleCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), value, event.count(), event.offsetX(), event.offsetY(), event.offsetZ(), event.speed())));
				context.setItem(11, promptButton(player, event, Material.GLOWSTONE_DUST, "<#f7d774><bold>Set Count", CinematicEditorLore.edit("Current count", String.valueOf(event.count())), "Type the particle count.", value ->
					new ParticleCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.particle(), CinematicEditorParsing.positiveInt(value), event.offsetX(), event.offsetY(), event.offsetZ(), event.speed())));
				context.setItem(12, promptButton(player, event, Material.SUGAR, "<#f7d774><bold>Set Speed", CinematicEditorLore.edit("Current speed", String.valueOf(event.speed())), "Type the particle speed.", value ->
					new ParticleCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.particle(), event.count(), event.offsetX(), event.offsetY(), event.offsetZ(), CinematicEditorParsing.decimal(value))));
				context.setItem(13, promptButton(player, event, Material.MAP, "<#30bbf1><bold>Set Offsets", CinematicEditorLore.edit("Current offsets", event.offsetX() + " " + event.offsetY() + " " + event.offsetZ()), "Type offsets as: x y z", value -> {
					double[] vector = CinematicEditorParsing.vector3(value);
					return new ParticleCinematicEvent(event.tick(), event.timelineSlot(), event.row(), event.position(), event.particle(), event.count(), vector[0], vector[1], vector[2], event.speed());
				}));
				context.setItem(14, button(Material.ENDER_EYE, "<#42d829><bold>Capture Location", CinematicEditorLore.captureLocation(event.position()), click -> {
					ParticleCinematicEvent updated = new ParticleCinematicEvent(
						event.tick(), event.timelineSlot(), event.row(), CinematicEventPosition.at(player.getLocation()), event.particle(), event.count(),
						event.offsetX(), event.offsetY(), event.offsetZ(), event.speed()
					);
					editor.replaceEvent(player, event, updated);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
				context.setItem(15, promptButton(player, event, Material.CLOCK, "<#f7d774><bold>Set Tick", CinematicEditorLore.edit("Current tick", CinematicTimeFormat.tickTime(event.tick())), "Type the new tick.", value ->
					new ParticleCinematicEvent(CinematicEditorParsing.nonNegativeInt(value), event.timelineSlot(), event.row(), event.position(), event.particle(), event.count(), event.offsetX(), event.offsetY(), event.offsetZ(), event.speed())));
				context.setItem(16, promptButton(player, event, Material.HOPPER, "<#f7d774><bold>Set Row", CinematicEditorLore.edit("Current row", String.valueOf(event.row())), "Type the new row. Row 0 is reserved for cameras.", value ->
					new ParticleCinematicEvent(event.tick(), event.timelineSlot(), CinematicEditorParsing.positiveInt(value), event.position(), event.particle(), event.count(), event.offsetX(), event.offsetY(), event.offsetZ(), event.speed())));
				context.setItem(18, button(
					Material.REPEATER,
					"<#f7d774><bold>Move Slot",
					CinematicEditorClickSteps.slotControlLore("this particle event"),
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
				context.setItem(26, button(Material.REDSTONE_BLOCK, "<#d43030><bold>Delete", List.of("<gray>Remove this particle event."), click -> {
					editor.removeEvent(player, event);
					click.actions().transition(() -> editor.openTimeline(player));
				}));
			})
			.build()
			.open(menus, player);
	}

	private io.voluble.michellelib.menu.item.MenuItem promptButton(
		Player player,
		ParticleCinematicEvent event,
		Material material,
		String name,
		List<String> lore,
		String prompt,
		Function<String, ParticleCinematicEvent> mapper
	) {
		return button(material, name, lore, click -> {
			editor.input().prompt(
				player,
				prompt,
				value -> {
					try {
						ParticleCinematicEvent updated = mapper.apply(value);
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
