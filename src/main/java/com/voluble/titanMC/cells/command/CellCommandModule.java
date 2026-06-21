package com.voluble.titanMC.cells.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.cells.CellManager;
import com.voluble.titanMC.cells.CellResetService;
import com.voluble.titanMC.cells.CellSignRenderer;
import com.voluble.titanMC.cells.CellSignService;
import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.selection.SelectionException;
import com.voluble.titanMC.regions.selection.WorldEditRegionSelection;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.util.RegionUtils;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.suggest.Suggest;
import io.voluble.michellelib.commands.tree.CommandTree;
import io.voluble.michellelib.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CellCommandModule implements CommandModule {
	private final CellManager cells;
	private final CellResetService resets;
	private final CellSignService signs;
	private final CellSignRenderer renderer;

	public CellCommandModule(
		CellManager cells,
		CellResetService resets,
		CellSignService signs,
		CellSignRenderer renderer
	) {
		this.cells = cells;
		this.resets = resets;
		this.signs = signs;
		this.renderer = renderer;
	}

	@Override
	public void register(CommandRegistration registration) {
		var names = Suggest.fromContext(source -> cells.cells().stream().map(CellDefinition::id).toList());
		registration.register(CommandTree.root("cell")
			.aliases("cells")
			.description("Manage rentable cells")
			.requiresPermission("titanmc.cell.admin")
			.requiresPlayerExecutor()
			.executes(this::root)
			.literalExec("list", this::list)
			.literal("create", node -> node
				.argument("name", Args.word(), name -> name
					.argument("price", Args.longArg(), price -> price
						.argument("duration", Args.word(), duration -> duration
							.argument("max_duration", Args.word(), maximum -> maximum.executes(this::create))))))
			.literal("delete", node -> node.argument("name", Args.word(), name -> name.suggests(names).executes(this::delete)))
			.literal("info", node -> node.argument("name", Args.word(), name -> name.suggests(names).executes(this::info)))
			.literal("displayname", node -> node.argument("name", Args.word(), name -> name.suggests(names)
				.argument("display_name", Args.greedyString(), value -> value.executes(this::displayName))))
			.literal("reset", node -> node.argument("name", Args.word(), name -> name.suggests(names).executes(this::reset)))
			.literal("member", node -> node
				.literal("add", add -> add.argument("name", Args.word(), name -> name.suggests(names)
					.argument("player", Args.word(), player -> player.executes(context -> member(context, true)))))
				.literal("remove", remove -> remove.argument("name", Args.word(), name -> name.suggests(names)
					.argument("player", Args.word(), player -> player.executes(context -> member(context, false)))))
				.literal("list", list -> list.argument("name", Args.word(), name -> name.suggests(names).executes(this::members))))
			.literal("sign", node -> node.argument("name", Args.word(), name -> name.suggests(names).executes(this::sign)))
			.spec());
	}

	private int root(MichelleCommandContext context) throws CommandSyntaxException {
		context.playerExecutor().sendMessage("Usage: /cell <create|delete|list|info|displayname|reset|sign|member>");
		return CommandTree.ok();
	}

	private int create(MichelleCommandContext context) throws CommandSyntaxException {
		Player player = context.playerExecutor();
		try {
			var selected = WorldEditRegionSelection.read(player);
			if (!(selected.geometry() instanceof CuboidGeometry cuboid)) {
				player.sendMessage("Cells must use a cuboid selection.");
				return CommandTree.ok();
			}

			Duration duration = parseDuration(context, "duration");
			Duration maximum = parseDuration(context, "max_duration");
			var bounds = cuboid.bounds();
			CellDefinition cell = new CellDefinition(
				context.arg("name", String.class),
				WardId.of("e"),
				new RegionUtils.Cuboid(
					selected.worldId(),
					bounds.minX(), bounds.minY(), bounds.minZ(),
					bounds.maxXExclusive() - 1, bounds.maxYExclusive() - 1, bounds.maxZExclusive() - 1
				),
				context.arg("price", Long.class),
				duration.toSeconds(),
				maximum.toSeconds(),
				true
			);
			cells.create(cell);
			player.sendMessage("Created cell '" + cell.id() + "'.");
		} catch (SelectionException | RuntimeException exception) {
			player.sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}

	private Duration parseDuration(MichelleCommandContext context, String argument) {
		return TimeParser.parse(context.arg(argument, String.class));
	}

	private int delete(MichelleCommandContext context) throws CommandSyntaxException {
		try {
			cells.delete(context.arg("name", String.class));
			context.playerExecutor().sendMessage("Deleted cell.");
		} catch (RuntimeException exception) {
			context.playerExecutor().sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}

	private int list(MichelleCommandContext context) throws CommandSyntaxException {
		String value = cells.cells().isEmpty()
			? "none"
			: cells.cells().stream().map(CellDefinition::id).collect(Collectors.joining(", "));
		context.playerExecutor().sendMessage("Cells: " + value);
		return CommandTree.ok();
	}

	private int info(MichelleCommandContext context) throws CommandSyntaxException {
		CellDefinition cell = cells.get(context.arg("name", String.class));
		if (cell == null) {
			context.playerExecutor().sendMessage("Unknown cell.");
			return CommandTree.ok();
		}
		var lease = cells.lease(cell.id());
		context.playerExecutor().sendMessage(
			cell.displayName() + " (" + cell.id() + ") | $" + cell.rentPrice()
				+ " | duration " + cell.rentDurationSeconds() + "s"
				+ " | maximum " + cell.maxRentDurationSeconds() + "s"
				+ " | " + (lease == null ? "available" : "rented by " + lease.ownerId())
		);
		return CommandTree.ok();
	}

	private int displayName(MichelleCommandContext context) throws CommandSyntaxException {
		try {
			String id = context.arg("name", String.class);
			cells.setDisplayName(id, context.arg("display_name", String.class));
			renderer.refresh(cells.get(id));
			context.playerExecutor().sendMessage("Updated cell display name.");
		} catch (RuntimeException exception) {
			context.playerExecutor().sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}

	private int reset(MichelleCommandContext context) throws CommandSyntaxException {
		try {
			resets.reset(context.arg("name", String.class));
			context.playerExecutor().sendMessage("Cell reset started.");
		} catch (RuntimeException exception) {
			context.playerExecutor().sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}

	private int member(MichelleCommandContext context, boolean add) throws CommandSyntaxException {
		String input = context.arg("player", String.class);
		OfflinePlayer target;
		try {
			target = Bukkit.getOfflinePlayer(UUID.fromString(input));
		} catch (IllegalArgumentException ignored) {
			target = Bukkit.getOfflinePlayerIfCached(input);
		}
		if (target == null) {
			context.playerExecutor().sendMessage("Unknown player. Use a cached name or UUID.");
			return CommandTree.ok();
		}
		try {
			if (add) cells.addMember(context.arg("name", String.class), target.getUniqueId());
			else cells.removeMember(context.arg("name", String.class), target.getUniqueId());
			context.playerExecutor().sendMessage(
				(add ? "Added " : "Removed ")
					+ (target.getName() == null ? target.getUniqueId() : target.getName())
					+ (add ? " to " : " from ") + "the cell."
			);
		} catch (RuntimeException exception) {
			context.playerExecutor().sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}

	private int members(MichelleCommandContext context) throws CommandSyntaxException {
		var values = cells.members(context.arg("name", String.class));
		context.playerExecutor().sendMessage("Members: " + (values.isEmpty() ? "none" : values));
		return CommandTree.ok();
	}

	private int sign(MichelleCommandContext context) throws CommandSyntaxException {
		var block = context.playerExecutor().getTargetBlockExact(6);
		if (block == null || !(block.getState() instanceof Sign sign)) {
			context.playerExecutor().sendMessage("Look at a sign within 6 blocks.");
			return CommandTree.ok();
		}
		try {
			signs.bind(sign, context.arg("name", String.class));
			context.playerExecutor().sendMessage("Rental sign linked.");
		} catch (RuntimeException exception) {
			context.playerExecutor().sendMessage(exception.getMessage());
		}
		return CommandTree.ok();
	}
}
