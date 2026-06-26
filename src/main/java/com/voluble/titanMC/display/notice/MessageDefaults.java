package com.voluble.titanMC.display.notice;

import java.util.List;

public final class MessageDefaults {
	public static final MessageDefinition COMMAND_PLAYER_ONLY = MessageDefinition.of(
		"command.player-only", MessageType.ERROR, "Only players can use this command."
	);
	public static final MessageDefinition DONATOR_TOOLS_HELP_TITLE = MessageDefinition.of(
		"donator-tools.help.title", MessageType.INFO, "Donator Tools"
	);
	public static final MessageDefinition DONATOR_TOOLS_HELP_USAGE = MessageDefinition.of(
		"donator-tools.help.usage", MessageType.INFO, "/dtools <tool> [player]\n/dtools reload"
	);
	public static final MessageDefinition DONATOR_TOOLS_HELP_TOOL = MessageDefinition.of(
		"donator-tools.help.tool", MessageType.INFO, "{{tool}} - {{description}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOAD_DENIED = MessageDefinition.of(
		"donator-tools.reload.denied", MessageType.ERROR, "You may not reload donator tools."
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOADED = MessageDefinition.of(
		"donator-tools.reload.success", MessageType.SUCCESS, "Donator tools reloaded."
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOAD_FAILED = MessageDefinition.of(
		"donator-tools.reload.failed", MessageType.ERROR, "Donator tools reload failed: {{reason}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_GIVE_DENIED = MessageDefinition.of(
		"donator-tools.give.denied", MessageType.ERROR, "You may not give donator tools."
	);
	public static final MessageDefinition DONATOR_TOOLS_UNKNOWN = MessageDefinition.of(
		"donator-tools.unknown", MessageType.ERROR, "Unknown tool. Available: {{tools}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_INVENTORY_FULL = MessageDefinition.of(
		"donator-tools.inventory-full", MessageType.INFO,
		"Your inventory was full, so the {{tool}} was dropped."
	);
	public static final MessageDefinition DONATOR_TOOLS_RECEIVED = MessageDefinition.of(
		"donator-tools.received", MessageType.SUCCESS, "You received a {{tool}}."
	);
	public static final MessageDefinition DONATOR_TOOLS_GAVE = MessageDefinition.of(
		"donator-tools.gave", MessageType.SUCCESS, "Gave {{tool}} to {{player}}."
	);

	private static final List<MessageDefinition> ALL = List.of(
		COMMAND_PLAYER_ONLY,
		DONATOR_TOOLS_HELP_TITLE,
		DONATOR_TOOLS_HELP_USAGE,
		DONATOR_TOOLS_HELP_TOOL,
		DONATOR_TOOLS_RELOAD_DENIED,
		DONATOR_TOOLS_RELOADED,
		DONATOR_TOOLS_RELOAD_FAILED,
		DONATOR_TOOLS_GIVE_DENIED,
		DONATOR_TOOLS_UNKNOWN,
		DONATOR_TOOLS_INVENTORY_FULL,
		DONATOR_TOOLS_RECEIVED,
		DONATOR_TOOLS_GAVE
	);

	private MessageDefaults() {
	}

	public static List<MessageDefinition> all() {
		return ALL;
	}
}
