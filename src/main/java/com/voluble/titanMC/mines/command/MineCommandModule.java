package com.voluble.titanMC.mines.command;

import com.voluble.titanMC.TitanMC;
import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.mines.MineMessages;
import com.voluble.titanMC.mines.MineValidation;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.mines.gui.MineListMenu;
import com.voluble.titanMC.regions.selection.SelectionException;
import com.voluble.titanMC.mines.selection.WorldEditSelection;
import com.voluble.titanMC.util.RegionUtils;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.suggest.Suggest;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MineCommandModule implements CommandModule {

	private final TitanMC plugin;

	public MineCommandModule(TitanMC plugin) {
		this.plugin = plugin;
	}

	private MineManager manager() {
		return plugin.getMineManager();
	}

	@Override
	public void register(CommandRegistration registration) {
		var mineNames = Suggest.fromContext(source ->
			manager().getAll().stream().map(Mine::getName).collect(Collectors.toList()));
		List<String> materialNames = Arrays.stream(Material.values()).map(Enum::name).toList();

		registration.register(
			CommandTree.root("mine")
				.description("Manage mines")
				.requiresPermission("titanmc.mine.admin")
				.requiresPlayerExecutor()
				.executes(this::handleRoot)
				.literalExec("menu", this::handleRoot)
				.literalExec("list", this::handleList)
				.literal("create", createNode -> createNode
					.argument("name", Args.word(), nameNode -> nameNode
						.executes(this::handleCreate)))
				.literal("redefine", posNode -> posNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.executes(this::handleRedefine)))
				.literal("setinterval", setNode -> setNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.argument("seconds", Args.integer(1), secNode -> secNode
							.executes(this::handleSetInterval))))
				.literal("setdepletion", setNode -> setNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.argument("percent", Args.integer(-1, 100), pctNode -> pctNode
							.executes(this::handleSetDepletion))))
				.literal("setsafespawn", setNode -> setNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.executes(this::handleSetSafeSpawn)))
				.literal("addblock", addNode -> addNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.argument("material", Args.word(), matNode -> matNode
							.suggestStrings(materialNames)
							.argument("weight", Args.integer(1), weightNode -> weightNode
								.executes(this::handleAddBlock)))))
				.literal("removeblock", remNode -> remNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.argument("material", Args.word(), matNode -> matNode
							.suggestStrings(materialNames)
							.executes(this::handleRemoveBlock))))
				.literal("forcereset", forceNode -> forceNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.executes(this::handleForceReset)))
				.literal("template", templateNode -> templateNode
					.literal("capture", captureNode -> captureNode
						.argument("name", Args.word(), nameNode -> nameNode
							.suggests(mineNames)
							.argument("template", Args.word(), templateId -> templateId
								.executes(this::handleTemplateCapture)))))
				.literal("resetmode", modeNode -> modeNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.literalExec("palette", this::handlePaletteReset)
						.literal("template", templateNode -> templateNode
							.argument("template", Args.word(), templateId -> templateId
								.executes(this::handleTemplateUse)))))
				.literal("delete", delNode -> delNode
					.argument("name", Args.word(), nameNode -> nameNode
						.suggests(mineNames)
						.executes(this::handleDelete)))
				.spec()
		);
	}

	private int handleRoot(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		if (manager().getAll().isEmpty()) {
			MineMessages.sendNoMinesInstructions(player);
			return CommandTree.ok();
		}
		return openMenu(ctx);
	}

	private int openMenu(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		MineListMenu.open(player, manager());
		return CommandTree.ok();
	}

	private int handleList(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		if (manager().getAll().isEmpty()) {
			MineMessages.sendNoMinesInstructions(player);
			return CommandTree.ok();
		}
		String list = manager().getAll().stream().map(Mine::getName).collect(Collectors.joining(", "));
		player.sendMessage("Mines: " + list);
		return CommandTree.ok();
	}

	private int handleCreate(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		String nameError = MineValidation.validateName(name);
		if (nameError != null) {
			player.sendMessage(nameError);
			return CommandTree.ok();
		}
		if (manager().exists(name)) {
			player.sendMessage("Mine already exists.");
			return CommandTree.ok();
		}
		RegionUtils.Cuboid c = getSelection(player);
		if (c == null || !validateSelection(player, c, null)) return CommandTree.ok();
		WeightedPalette palette = new WeightedPalette();
		palette.addOrUpdate(Material.STONE, 1);
		Mine mine = new Mine(name, c, 900, true, 1500, palette);
		manager().add(mine);
		player.sendMessage("Created mine '" + name + "' from your WorldEdit selection.");
		MineListMenu.open(player, manager());
		return CommandTree.ok();
	}

	private int handleRedefine(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		Mine mine = manager().get(name);
		if (mine == null) {
			player.sendMessage("Unknown mine.");
			return CommandTree.ok();
		}
		RegionUtils.Cuboid updated = getSelection(player);
		if (updated == null || !validateSelection(player, updated, name)) return CommandTree.ok();
		manager().setCuboid(name, updated);
		player.sendMessage("Redefined '" + name + "' from your WorldEdit selection.");
		return CommandTree.ok();
	}

	private RegionUtils.Cuboid getSelection(Player player) {
		try {
			return WorldEditSelection.getCuboid(player);
		} catch (SelectionException exception) {
			player.sendMessage(exception.getMessage());
			return null;
		}
	}

	private boolean validateSelection(Player player, RegionUtils.Cuboid cuboid, String excludedName) {
		String cuboidError = MineValidation.validateCuboid(cuboid);
		if (cuboidError != null) {
			player.sendMessage(cuboidError);
			return false;
		}
		Mine overlap = manager().findOverlap(cuboid, excludedName);
		if (overlap != null) {
			player.sendMessage("That selection overlaps mine '" + overlap.getName() + "'.");
			return false;
		}
		return true;
	}

	private int handleSetInterval(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		int seconds = ctx.arg("seconds", Integer.class);
		if (!requireMine(player, name)) return CommandTree.ok();
		manager().setInterval(name, seconds);
		player.sendMessage("Set interval for '" + name + "' to " + seconds + "s.");
		return CommandTree.ok();
	}

	private int handleSetDepletion(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		int percent = ctx.arg("percent", Integer.class);
		Mine mine = manager().get(name);
		if (mine == null) {
			player.sendMessage("Unknown mine.");
			return CommandTree.ok();
		}
		manager().setDepletionThreshold(name, percent);
		player.sendMessage("Depletion auto-reset for '" + name + "' set to " + percent + "%. (-1 disables)");
		return CommandTree.ok();
	}

	private int handleSetSafeSpawn(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		Mine mine = manager().get(name);
		if (mine == null) {
			player.sendMessage("Unknown mine.");
			return CommandTree.ok();
		}
		manager().setSafeSpawn(name, player.getLocation().clone());
		player.sendMessage("Set safe spawn for '" + name + "'.");
		return CommandTree.ok();
	}

	private int handleAddBlock(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		Material mat;
		try {
			mat = Material.valueOf(ctx.arg("material", String.class).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			player.sendMessage("Invalid material.");
			return CommandTree.ok();
		}
		int weight = ctx.arg("weight", Integer.class);
		if (!requireMine(player, name)) return CommandTree.ok();
		manager().paletteAddOrUpdate(name, mat, weight);
		player.sendMessage("Palette updated: " + mat + "=" + weight);
		return CommandTree.ok();
	}

	private int handleRemoveBlock(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		Material mat;
		try {
			mat = Material.valueOf(ctx.arg("material", String.class).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			player.sendMessage("Invalid material.");
			return CommandTree.ok();
		}
		if (!requireMine(player, name)) return CommandTree.ok();
		manager().paletteRemove(name, mat);
		player.sendMessage("Removed from palette: " + mat);
		return CommandTree.ok();
	}

	private int handleForceReset(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		if (plugin.getMineScheduler().forceReset(name)) {
			player.sendMessage("Force reset triggered for '" + name + "'.");
		} else {
			player.sendMessage("Unknown mine.");
		}
		return CommandTree.ok();
	}

	private int handleDelete(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		if (manager().delete(name)) {
			player.sendMessage("Deleted mine '" + name + "'. The blocks were left untouched.");
		} else {
			player.sendMessage("Unknown mine.");
		}
		return CommandTree.ok();
	}

	private int handleTemplateCapture(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		String name = ctx.arg("name", String.class);
		String templateId = ctx.arg("template", String.class);
		Mine mine = manager().get(name);
		if (mine == null) {
			player.sendMessage("Unknown mine.");
			return CommandTree.ok();
		}
		plugin.getMineScheduler().cancelReset(name);
		try {
			if (!manager().templates().capture(mine, templateId, result -> {
				if (result.successful()) {
					try {
						manager().setTemplateReset(name, templateId);
						player.sendMessage(result.message() + " Template reset is now active.");
					} catch (RuntimeException failure) {
						player.sendMessage("The template was captured but could not be activated: " + failure.getMessage());
					}
				} else player.sendMessage(result.message());
			})) {
				player.sendMessage("That mine is already being captured.");
				return CommandTree.ok();
			}
			player.sendMessage("Capturing template " + templateId + " from " + name + "...");
		} catch (IllegalArgumentException failure) {
			player.sendMessage(failure.getMessage());
		}
		return CommandTree.ok();
	}

	private int handleTemplateUse(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		try {
			manager().setTemplateReset(ctx.arg("name", String.class), ctx.arg("template", String.class));
			player.sendMessage("Template reset enabled.");
		} catch (RuntimeException failure) {
			player.sendMessage(failure.getMessage());
		}
		return CommandTree.ok();
	}

	private int handlePaletteReset(MichelleCommandContext ctx) throws CommandSyntaxException {
		Player player = ctx.playerExecutor();
		try {
			manager().setPaletteReset(ctx.arg("name", String.class));
			player.sendMessage("Palette reset enabled.");
		} catch (RuntimeException failure) {
			player.sendMessage(failure.getMessage());
		}
		return CommandTree.ok();
	}

	private boolean requireMine(Player player, String name) {
		if (manager().exists(name)) return true;
		player.sendMessage("Unknown mine.");
		return false;
	}
}
