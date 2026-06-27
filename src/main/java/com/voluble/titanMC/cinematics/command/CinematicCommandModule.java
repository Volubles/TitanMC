package com.voluble.titanMC.cinematics.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.cinematics.config.CinematicConfigurationManager;
import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.cinematics.runtime.CinematicRuntime;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.suggest.Suggest;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.stream.Collectors;

public final class CinematicCommandModule implements CommandModule {
	private static final String ADMIN_PERMISSION = "titanmc.cinematic.admin";

	private final CinematicConfigurationManager configuration;
	private final CinematicRuntime runtime;
	private final PluginMessageService messages;

	public CinematicCommandModule(
		CinematicConfigurationManager configuration,
		CinematicRuntime runtime,
		PluginMessageService messages
	) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.messages = Objects.requireNonNull(messages, "messages");
	}

	@Override
	public void register(CommandRegistration registration) {
		var ids = Suggest.fromContext(source -> configuration.current().cinematics().keySet().stream()
			.map(CinematicId::value)
			.sorted()
			.toList());
		registration.register(CommandTree.root("cinematic")
			.aliases("cinematics")
			.description("Manage Titan cinematics")
			.requiresPermission(ADMIN_PERMISSION)
			.literalExec("list", this::list)
			.literalExec("reload", this::reload)
			.literal("create", node -> node
				.argument("id", Args.word(), id -> id.executes(this::create)))
			.literal("play", node -> node
				.argument("id", Args.word(), id -> id.suggests(ids).executes(this::play)))
			.literal("stop", node -> node.executesPlayer((player, context) -> stop(player)))
			.literal("point", point -> point
				.literal("add", add -> add
					.argument("id", Args.word(), id -> id.suggests(ids).executes(this::addPoint))))
			.spec());
	}

	private int list(MichelleCommandContext context) {
		String list = configuration.current().cinematics().keySet().stream()
			.map(CinematicId::value)
			.sorted()
			.collect(Collectors.joining(", "));
		messages.send(context.sender(), MessageDefaults.CINEMATICS_LIST, args -> args.plain("cinematics", list.isBlank() ? "none" : list));
		return CommandTree.ok();
	}

	private int reload(MichelleCommandContext context) {
		configuration.reload();
		messages.send(context.sender(), MessageDefaults.CINEMATICS_RELOADED);
		return CommandTree.ok();
	}

	private int create(MichelleCommandContext context) throws CommandSyntaxException {
		CinematicId id = id(context.arg("id", String.class));
		configuration.createIfMissing(id);
		messages.send(context.sender(), MessageDefaults.CINEMATICS_CREATED, args -> args.plain("cinematic", id.value()));
		return CommandTree.ok();
	}

	private int play(MichelleCommandContext context) throws CommandSyntaxException {
		Player player = context.playerExecutor();
		CinematicId id = id(context.arg("id", String.class));
		CinematicRuntime.StartResult result = runtime.start(player, id);
		switch (result) {
			case STARTED -> messages.send(player, MessageDefaults.CINEMATICS_STARTED, args -> args.plain("cinematic", id.value()));
			case DISABLED -> messages.send(player, MessageDefaults.CINEMATICS_DISABLED);
			case UNKNOWN -> messages.send(player, MessageDefaults.CINEMATICS_UNKNOWN, args -> args.plain("cinematic", id.value()));
		}
		return CommandTree.ok();
	}

	private int stop(Player player) {
		boolean stopped = runtime.stop(player.getUniqueId(), true);
		messages.send(player, stopped ? MessageDefaults.CINEMATICS_STOPPED : MessageDefaults.CINEMATICS_NOT_ACTIVE);
		return CommandTree.ok();
	}

	private int addPoint(MichelleCommandContext context) throws CommandSyntaxException {
		Player player = context.playerExecutor();
		CinematicId id = id(context.arg("id", String.class));
		int tick = configuration.nextPointTick(id);
		configuration.appendCameraPoint(id, CameraPoint.at(tick, player.getLocation()));
		messages.send(player, MessageDefaults.CINEMATICS_POINT_ADDED, args -> args
			.plain("cinematic", id.value())
			.plain("tick", tick));
		return CommandTree.ok();
	}

	private static CinematicId id(String input) throws CommandSyntaxException {
		try {
			return CinematicId.of(input);
		} catch (IllegalArgumentException exception) {
			throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(exception.getMessage());
		}
	}
}
