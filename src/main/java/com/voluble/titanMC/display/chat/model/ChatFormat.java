package com.voluble.titanMC.display.chat.model;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ChatFormat(
		ChatFormatId id,
		String permission,
		int weight,
		ChatFormatSegment prefix,
		ChatFormatSegment name,
		ChatFormatSegment suffix,
		ChatFormatSegment message
) {
	public ChatFormat {
		Objects.requireNonNull(id, "id");
		permission = Objects.requireNonNull(permission, "permission").trim();
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(suffix, "suffix");
		Objects.requireNonNull(message, "message");
	}

	public boolean matches(Player player) {
		Objects.requireNonNull(player, "player");
		return permission.isEmpty() || player.hasPermission(permission);
	}

	public ChatFormatSegment segment(ChatFormatSlot slot) {
		Objects.requireNonNull(slot, "slot");
		return switch (slot) {
			case PREFIX -> prefix;
			case NAME -> name;
			case SUFFIX -> suffix;
			case MESSAGE -> message;
		};
	}

	public List<Map.Entry<ChatFormatSlot, ChatFormatSegment>> orderedSegments() {
		return List.of(
			Map.entry(ChatFormatSlot.PREFIX, prefix),
			Map.entry(ChatFormatSlot.NAME, name),
			Map.entry(ChatFormatSlot.SUFFIX, suffix),
			Map.entry(ChatFormatSlot.MESSAGE, message)
		);
	}
}
