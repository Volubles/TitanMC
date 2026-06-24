package com.voluble.titanMC.progression.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import com.voluble.titanMC.progression.service.ProgressionUpdate;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class CredCommandModule implements CommandModule {
	private static final String USE_PERMISSION = "titanmc.cred.use";
	private static final String ADMIN_PERMISSION = "titanmc.cred.admin";
	private static final CredSource ADMIN_SOURCE = CredSource.of("admin");

	private final ProgressionEngine engine;

	public CredCommandModule(ProgressionEngine engine) {
		this.engine = Objects.requireNonNull(engine, "engine");
	}

	@Override
	public void register(CommandRegistration registration) {
		registration.register(CommandTree.root("cred")
			.description("Show or manage cred and player levels")
			.requiresAnyPermission(USE_PERMISSION, ADMIN_PERMISSION)
			.executesPlayer((player, ctx) -> showOwn(player))
			.literal("info", node -> node
				.requiresPermission(ADMIN_PERMISSION)
				.argument("player", Args.word(), name -> name.executes(this::info)))
			.literal("give", node -> node
				.requiresPermission(ADMIN_PERMISSION)
				.argument("player", Args.word(), name -> name
					.argument("amount", Args.longArg(), amount -> amount
						.executes(context -> apply(context, true, null))
						.argument("source", Args.word(), source -> source
							.executes(context -> apply(context, true, context.arg("source", String.class)))))))
			.literal("take", node -> node
				.requiresPermission(ADMIN_PERMISSION)
				.argument("player", Args.word(), name -> name
					.argument("amount", Args.longArg(), amount -> amount.executes(context -> apply(context, false, null)))))
			.spec());
	}

	private int showOwn(Player player) {
		PlayerProgression progression = engine.current(player.getUniqueId());
		player.sendMessage(formatSelf(progression));
		return CommandTree.ok();
	}

	private int info(MichelleCommandContext context) throws CommandSyntaxException {
		CommandSender sender = context.sender();
		OfflinePlayer target = resolvePlayer(context.arg("player", String.class));
		if (target == null) {
			sender.sendMessage("Unknown player. Use a cached name or UUID.");
			return CommandTree.ok();
		}
		PlayerProgression progression = engine.current(target.getUniqueId());
		sender.sendMessage(displayName(target) + ": " + formatLine(progression));
		return CommandTree.ok();
	}

	private int apply(MichelleCommandContext context, boolean give, String sourceArg) throws CommandSyntaxException {
		CommandSender sender = context.sender();
		OfflinePlayer target = resolvePlayer(context.arg("player", String.class));
		if (target == null) {
			sender.sendMessage("Unknown player. Use a cached name or UUID.");
			return CommandTree.ok();
		}
		long amount = context.arg("amount", Long.class);
		if (amount <= 0) {
			sender.sendMessage("Amount must be positive.");
			return CommandTree.ok();
		}
		CredSource source;
		try {
			source = sourceArg == null ? ADMIN_SOURCE : CredSource.of(sourceArg);
		} catch (IllegalArgumentException exception) {
			sender.sendMessage("Invalid source id: " + exception.getMessage());
			return CommandTree.ok();
		}
		CredAmount credAmount = CredAmount.of(amount);
		ProgressionUpdate update = give
			? engine.give(target.getUniqueId(), credAmount, source)
			: engine.take(target.getUniqueId(), credAmount, source);

		String verb = give ? "Gave" : "Took";
		String name = displayName(target);
		if (update.applied() == 0L) {
			sender.sendMessage(name + " was unchanged (already at the boundary).");
			return CommandTree.ok();
		}
		String credChange = (give ? "+" : "") + update.applied() + " cred";
		String levelChange = update.changedLevel()
			? " (level " + update.previous().level() + " -> " + update.current().level() + ")"
			: "";
		sender.sendMessage(verb + " " + name + " " + credChange + levelChange + " [" + source.value() + "]");
		return CommandTree.ok();
	}

	private String formatSelf(PlayerProgression progression) {
		long current = progression.totalCred();
		int level = progression.level();
		long currentLevelStart = engine.curve().credForLevel(level);
		long nextLevelStart = level >= engine.maxLevel() ? current : engine.curve().credForLevel(level + 1);
		if (level >= engine.maxLevel()) {
			return "Cred: " + current + " | level " + level + " (max)";
		}
		long inLevel = current - currentLevelStart;
		long span = nextLevelStart - currentLevelStart;
		long percent = span == 0 ? 100 : (100L * inLevel) / span;
		return "Cred: " + current + " | level " + level
			+ " | " + inLevel + " / " + span + " to level " + (level + 1) + " (" + percent + "%)";
	}

	private String formatLine(PlayerProgression progression) {
		return progression.totalCred() + " cred, level " + progression.level();
	}

	private static OfflinePlayer resolvePlayer(String input) {
		try {
			return Bukkit.getOfflinePlayer(UUID.fromString(input));
		} catch (IllegalArgumentException ignored) {
			return Bukkit.getOfflinePlayerIfCached(input);
		}
	}

	private static String displayName(OfflinePlayer player) {
		return player.getName() == null ? player.getUniqueId().toString() : player.getName();
	}
}
