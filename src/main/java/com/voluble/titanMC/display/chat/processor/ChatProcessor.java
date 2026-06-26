package com.voluble.titanMC.display.chat.processor;

import net.kyori.adventure.text.Component;

public interface ChatProcessor {
	String id();

	Component process(ChatProcessorContext context, Component message);
}
