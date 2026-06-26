package com.voluble.titanMC.display.chat.service;

import com.voluble.titanMC.display.chat.model.ChatFormat;
import com.voluble.titanMC.display.chat.model.ChatFormatId;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class ChatFormatCatalog {
	private final ChatFormat defaultFormat;
	private final Map<ChatFormatId, ChatFormat> formatsById;
	private final List<ChatFormat> ordered;

	public ChatFormatCatalog(ChatFormat defaultFormat, Collection<ChatFormat> additional) {
		this.defaultFormat = Objects.requireNonNull(defaultFormat, "defaultFormat");
		Objects.requireNonNull(additional, "additional");
		Map<ChatFormatId, ChatFormat> indexed = new LinkedHashMap<>();
		List<ChatFormat> orderedFormats = new ArrayList<>();
		indexed.put(defaultFormat.id(), defaultFormat);
		orderedFormats.add(defaultFormat);
		for (ChatFormat format : additional) {
			Objects.requireNonNull(format, "additional must not contain null");
			if (format.id().equals(defaultFormat.id())) {
				throw new IllegalArgumentException("additional format reuses the default id: " + format.id());
			}
			if (indexed.putIfAbsent(format.id(), format) != null) {
				throw new IllegalArgumentException("duplicate chat format id: " + format.id());
			}
			orderedFormats.add(format);
		}
		formatsById = Map.copyOf(indexed);
		ordered = List.copyOf(orderedFormats);
	}

	public ChatFormat defaultFormat() {
		return defaultFormat;
	}

	public List<ChatFormat> all() {
		return ordered;
	}

	public Optional<ChatFormat> findById(ChatFormatId id) {
		return Optional.ofNullable(formatsById.get(Objects.requireNonNull(id, "id")));
	}

	public ChatFormat selectFor(Player player) {
		Objects.requireNonNull(player, "player");
		return selectFor(player::hasPermission);
	}

	public ChatFormat selectFor(Predicate<String> permissionTest) {
		Objects.requireNonNull(permissionTest, "permissionTest");
		ChatFormat best = defaultFormat;
		for (ChatFormat candidate : ordered) {
			if (candidate == defaultFormat) continue;
			boolean matches = candidate.permission().isEmpty() || permissionTest.test(candidate.permission());
			if (matches && candidate.weight() > best.weight()) {
				best = candidate;
			}
		}
		return best;
	}
}
