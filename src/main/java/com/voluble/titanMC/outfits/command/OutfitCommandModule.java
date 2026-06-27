package com.voluble.titanMC.outfits.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.outfits.OutfitResult;
import com.voluble.titanMC.outfits.OutfitService;
import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.suggest.Suggest;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class OutfitCommandModule implements CommandModule {
	private static final String USE_PERMISSION = "titanmc.outfits.use";
	private static final String ADMIN_PERMISSION = "titanmc.outfits.admin";

	private final OutfitConfigurationManager configuration;
	private final OutfitService outfits;
	private final PluginMessageService messages;

	public OutfitCommandModule(
		OutfitConfigurationManager configuration,
		OutfitService outfits,
		PluginMessageService messages
	) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.outfits = Objects.requireNonNull(outfits, "outfits");
		this.messages = Objects.requireNonNull(messages, "messages");
	}

	@Override
	public void register(CommandRegistration registration) {
		var outfitIds = Suggest.fromContext(source -> configuration.current().outfits().keySet().stream()
			.map(OutfitId::value)
			.collect(java.util.stream.Collectors.collectingAndThen(
				java.util.stream.Collectors.toCollection(java.util.ArrayList::new),
				values -> {
					values.add("original");
					return values;
				}
			)));
		registration.register(CommandTree.root("outfit")
			.aliases("outfits")
			.description("Choose a server outfit")
			.requiresAnyPermission(USE_PERMISSION, ADMIN_PERMISSION)
			.executesPlayer((player, context) -> list(player))
			.literal("use", node -> node
				.requiresPermission(USE_PERMISSION)
				.argument("outfit", Args.word(), outfit -> outfit.suggests(outfitIds).executesPlayer(this::use)))
			.literalExec("status", context -> status(context.sender()))
			.literalExec("reload", context -> {
				if (!context.sender().hasPermission(ADMIN_PERMISSION)) {
					messages.send(context.sender(), MessageDefaults.COMMAND_RUNTIME_ERROR, args -> args.plain("reason", "Missing permission."));
					return CommandTree.ok();
				}
				configuration.reload();
				messages.send(context.sender(), MessageDefaults.OUTFITS_RELOADED);
				return CommandTree.ok();
			})
			.spec());
	}

	private int list(Player player) {
		messages.send(player, MessageDefaults.OUTFITS_HEADER);
		for (OutfitDefinition outfit : configuration.current().outfits().values()) {
			messages.send(player, MessageDefaults.OUTFITS_LIST_ENTRY, args -> args
				.plain("id", outfit.id().value())
				.plain("name", outfit.displayName()));
		}
		messages.send(player, MessageDefaults.OUTFITS_USE_HINT);
		return CommandTree.ok();
	}

	private int use(Player player, io.voluble.michellelib.commands.context.MichelleCommandContext context) throws CommandSyntaxException {
		String input = context.arg("outfit", String.class);
		if (input.equalsIgnoreCase("original")) {
			messages.send(player, MessageDefaults.OUTFITS_APPLYING_ORIGINAL);
			outfits.applyOriginal(player, result -> sendResult(player, result, "original"));
			return CommandTree.ok();
		}
		OutfitId outfitId;
		try {
			outfitId = OutfitId.of(input);
		} catch (IllegalArgumentException exception) {
			messages.send(player, MessageDefaults.OUTFITS_UNKNOWN, args -> args.plain("outfit", input));
			return CommandTree.ok();
		}
		messages.send(player, MessageDefaults.OUTFITS_APPLYING, args -> args.plain("outfit", outfitId.value()));
		outfits.applyOutfit(player, outfitId, result -> sendResult(player, result, outfitId.value()));
		return CommandTree.ok();
	}

	private int status(org.bukkit.command.CommandSender sender) {
		messages.send(sender, MessageDefaults.OUTFITS_STATUS, args -> args
			.plain("enabled", configuration.current().enabled())
			.plain("outfits", configuration.current().outfits().size()));
		return CommandTree.ok();
	}

	private void sendResult(Player player, OutfitResult result, String outfit) {
		switch (result) {
			case APPLIED -> messages.send(player, MessageDefaults.OUTFITS_APPLIED, args -> args.plain("outfit", outfit));
			case ORIGINAL -> messages.send(player, MessageDefaults.OUTFITS_ORIGINAL);
			case DISABLED -> messages.send(player, MessageDefaults.OUTFITS_DISABLED);
			case UNKNOWN_OUTFIT -> messages.send(player, MessageDefaults.OUTFITS_UNKNOWN, args -> args.plain("outfit", outfit));
			case NO_MINESKIN_KEY -> messages.send(player, MessageDefaults.OUTFITS_NO_MINESKIN_KEY);
			case NO_ORIGINAL_SKIN -> messages.send(player, MessageDefaults.OUTFITS_NO_ORIGINAL_SKIN);
			case SKINS_RESTORER_UNAVAILABLE -> messages.send(player, MessageDefaults.OUTFITS_SKINS_RESTORER_UNAVAILABLE);
			case BUSY -> messages.send(player, MessageDefaults.OUTFITS_BUSY);
			case FAILED -> messages.send(player, MessageDefaults.OUTFITS_FAILED);
		}
	}
}
