package com.voluble.titanMC.display.chat.processor;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ChatProcessorRegistry {
	private final List<ChatProcessor> processors = new ArrayList<>();
	private final Set<String> ids = new LinkedHashSet<>();

	public void register(ChatProcessor processor) {
		Objects.requireNonNull(processor, "processor");
		String id = Objects.requireNonNull(processor.id(), "processor.id");
		if (id.isBlank()) throw new IllegalArgumentException("processor id must not be blank");
		if (!ids.add(id)) {
			throw new IllegalArgumentException("duplicate chat processor id: " + id);
		}
		processors.add(processor);
	}

	public List<ChatProcessor> all() {
		return List.copyOf(processors);
	}

	public Component process(ChatProcessorContext context, Component initial) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(initial, "initial");
		Component current = initial;
		for (ChatProcessor processor : processors) {
			Component next = processor.process(context, current);
			current = next == null ? current : next;
		}
		return current;
	}
}
