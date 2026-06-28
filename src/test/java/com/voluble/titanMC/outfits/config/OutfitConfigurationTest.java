package com.voluble.titanMC.outfits.config;

import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitRenderMode;
import com.voluble.titanMC.outfits.model.SkinModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutfitConfigurationTest {
	@TempDir
	private Path folder;

	@Test
	void loadsRenderModeAndSkinModelTemplates() {
		OutfitConfiguration configuration = OutfitConfiguration.load(yaml("""
			integrations:
			  mineskin:
			    visibility: "unlisted"
			outfits:
			  guard:
			    display-name: "Guard"
			    description:
			      - "Full guard skin."
			    render-mode: "full-skin"
			    skin-model: "slim"
			    classic-template: "templates/guard_classic.png"
			    slim-template: "templates/guard_slim.png"
			"""), folder);

		var outfit = configuration.find(OutfitId.of("guard")).orElseThrow();

		assertEquals(OutfitRenderMode.FULL_SKIN, outfit.renderMode());
		assertEquals(SkinModel.SLIM, outfit.skinModel());
		assertEquals(folder.resolve("templates/guard_classic.png").normalize(), outfit.templatePath(SkinModel.CLASSIC));
		assertEquals(folder.resolve("templates/guard_slim.png").normalize(), outfit.templatePath(SkinModel.SLIM));
	}

	@Test
	void legacyTemplateFallsBackForBothSkinModels() {
		OutfitConfiguration configuration = OutfitConfiguration.load(yaml("""
			integrations:
			  mineskin:
			    visibility: "unlisted"
			outfits:
			  prison:
			    display-name: "Prison"
			    template: "templates/prison_classic.png"
			"""), folder);

		var outfit = configuration.find(OutfitId.of("prison")).orElseThrow();

		assertEquals(OutfitRenderMode.COMPOSITE, outfit.renderMode());
		assertEquals(SkinModel.CLASSIC, outfit.skinModel());
		assertEquals(folder.resolve("templates/prison_classic.png").normalize(), outfit.templatePath(SkinModel.CLASSIC));
		assertEquals(folder.resolve("templates/prison_classic.png").normalize(), outfit.templatePath(SkinModel.SLIM));
	}

	@Test
	void legacyModelKeyStillSetsOutfitSkinModel() {
		OutfitConfiguration configuration = OutfitConfiguration.load(yaml("""
			integrations:
			  mineskin:
			    visibility: "unlisted"
			outfits:
			  guard:
			    display-name: "Guard"
			    render-mode: "full-skin"
			    model: "slim"
			    template: "templates/guard.png"
			"""), folder);

		var outfit = configuration.find(OutfitId.of("guard")).orElseThrow();

		assertEquals(SkinModel.SLIM, outfit.skinModel());
	}

	private static YamlConfiguration yaml(String source) {
		YamlConfiguration yaml = new YamlConfiguration();
		try {
			yaml.loadFromString(source);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return yaml;
	}
}
