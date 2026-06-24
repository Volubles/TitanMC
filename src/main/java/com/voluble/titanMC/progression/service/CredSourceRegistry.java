package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.model.CredSource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CredSourceRegistry {
	private final Map<CredSource, Entry> entries = new LinkedHashMap<>();

	public void register(CredSource id, String displayName, boolean enabled) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
		entries.put(id, new Entry(displayName, enabled));
	}

	public Optional<String> displayName(CredSource id) {
		Entry entry = entries.get(Objects.requireNonNull(id, "id"));
		return entry == null ? Optional.empty() : Optional.of(entry.displayName);
	}

	public boolean isEnabled(CredSource id) {
		Entry entry = entries.get(Objects.requireNonNull(id, "id"));
		return entry != null && entry.enabled;
	}

	public boolean isRegistered(CredSource id) {
		return entries.containsKey(Objects.requireNonNull(id, "id"));
	}

	public Collection<CredSource> registered() {
		return List.copyOf(entries.keySet());
	}

	private record Entry(String displayName, boolean enabled) {
	}
}
