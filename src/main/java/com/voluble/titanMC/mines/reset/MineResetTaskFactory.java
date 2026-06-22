package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.mines.template.MineTemplateStorage;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class MineResetTaskFactory {
	private final Plugin plugin;
	private final MineTemplateStorage templates;

	public MineResetTaskFactory(Plugin plugin, MineTemplateStorage templates) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.templates = Objects.requireNonNull(templates, "templates");
	}

	public MineResetTask create(Mine mine) {
		return switch (mine.getResetDefinition()) {
			case MineResetDefinition.Palette ignored -> new MineResetRunner(plugin, mine);
			case MineResetDefinition.Template template ->
				new TemplateMineResetTask(plugin, mine, templates.load(template.templateId()));
		};
	}
}
