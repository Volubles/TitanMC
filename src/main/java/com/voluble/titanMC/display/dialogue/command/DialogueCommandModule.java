package com.voluble.titanMC.display.dialogue.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.display.dialogue.DialogueDefinition;
import com.voluble.titanMC.display.dialogue.DialogueService;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class DialogueCommandModule implements CommandModule {
	private final DialogueService dialogues;
	private final DialogueDefinition previewDialogue;

	public DialogueCommandModule(DialogueService dialogues, DialogueDefinition previewDialogue) {
		this.dialogues = Objects.requireNonNull(dialogues, "dialogues");
		this.previewDialogue = Objects.requireNonNull(previewDialogue, "previewDialogue");
	}

	@Override
	public void register(CommandRegistration registration) {
		registration.register(CommandTree.root("dialogue")
			.description("Preview Titan display dialogues")
			.requiresPermission("titanmc.dialogue.admin")
			.requiresPlayerExecutor()
			.literalExec("preview", this::preview)
			.literalExec("skip", this::skip)
			.literalExec("clear", this::clear)
			.spec());
	}

	private int preview(MichelleCommandContext context) throws CommandSyntaxException {
		Player player = context.playerExecutor();
		dialogues.show(player, previewDialogue);
		return CommandTree.ok();
	}

	private int skip(MichelleCommandContext context) throws CommandSyntaxException {
		dialogues.skipTyping(context.playerExecutor());
		return CommandTree.ok();
	}

	private int clear(MichelleCommandContext context) throws CommandSyntaxException {
		dialogues.clear(context.playerExecutor());
		return CommandTree.ok();
	}
}
