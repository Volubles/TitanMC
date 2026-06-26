package com.voluble.titanMC.donatortools.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.donatortools.DonatorToolsService;
import com.voluble.titanMC.donatortools.item.DonatorToolRegistry;
import com.voluble.titanMC.donatortools.item.DonatorToolType;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.arguments.Resolve;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.errors.CommandErrors;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class DonatorToolsCommandModule implements CommandModule {

	private final DonatorToolsService service;
	private final DonatorToolRegistry tools;
	private final PluginMessageService messages;

	public DonatorToolsCommandModule(DonatorToolsService service, PluginMessageService messages) {
		this.service = Objects.requireNonNull(service, "service");
		this.messages = Objects.requireNonNull(messages, "messages");
		this.tools = service.registry();
	}

	@Override
	public void register(CommandRegistration registration) {
		registration.register(
				CommandTree.root("dtools")
						.description("Give a donator tool")
						.requiresAnyPermission("donatortools.give", "donatortools.reload")
						.executes(this::sendHelp)
						.literalExec("reload", this::reload)
						.argument("tool", Args.word(), tool -> tool
								.suggestStrings(tools.ids())
								.executes(this::giveToSelf)
								.argument("player", Args.player(), player -> player
										.executes(this::giveToPlayer)))
						.spec()
		);
	}

	private int sendHelp(MichelleCommandContext context) {
		messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_HELP_TITLE);
		messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_HELP_USAGE);
		for (DonatorToolType type : DonatorToolType.values()) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_HELP_TOOL, args -> args
				.plain("tool", type.id())
				.plain("description", type.description()));
		}
		return CommandTree.ok();
	}

	private int reload(MichelleCommandContext context) {
		if (!context.hasPermission("donatortools.reload")) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_RELOAD_DENIED);
			return CommandTree.ok();
		}
		try {
			service.reload();
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_RELOADED);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_RELOAD_FAILED,
				args -> args.plain("reason", exception.getMessage()));
		}
		return CommandTree.ok();
	}

	private int giveToSelf(MichelleCommandContext context) throws CommandSyntaxException {
		if (!(context.executor() instanceof Player player)) throw CommandErrors.playerOnly();
		return give(context, player);
	}

	private int giveToPlayer(MichelleCommandContext context) throws CommandSyntaxException {
		return give(context, Resolve.player(context, "player"));
	}

	private int give(MichelleCommandContext context, Player target) {
		if (!context.hasPermission("donatortools.give")) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_GIVE_DENIED);
			return CommandTree.ok();
		}
		String input = context.arg("tool", String.class);
		DonatorToolType type = tools.find(input).orElse(null);
		if (type == null) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_UNKNOWN,
				args -> args.plain("tools", String.join(", ", tools.ids())));
			return CommandTree.ok();
		}
		ItemStack item = tools.create(type);
		var remaining = target.getInventory().addItem(item);
		if (!remaining.isEmpty()) {
			target.getWorld().dropItemNaturally(target.getLocation(), item);
			messages.send(target, MessageDefaults.DONATOR_TOOLS_INVENTORY_FULL,
				args -> args.plain("tool", type.displayName()));
		} else {
			messages.send(target, MessageDefaults.DONATOR_TOOLS_RECEIVED,
				args -> args.plain("tool", type.displayName()));
		}
		if (!target.equals(context.sender())) {
			messages.send(context.sender(), MessageDefaults.DONATOR_TOOLS_GAVE, args -> args
				.plain("tool", type.displayName())
				.plain("player", target.getName()));
		}
		return CommandTree.ok();
	}
}
