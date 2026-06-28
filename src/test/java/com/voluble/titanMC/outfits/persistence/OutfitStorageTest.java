package com.voluble.titanMC.outfits.persistence;

import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitRenderMode;
import com.voluble.titanMC.outfits.model.SkinModel;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitStorageTest {
	@TempDir
	private Path folder;

	@Test
	void generatedSkinsAreKeyedByTemplateHash() throws Exception {
		UUID playerId = UUID.randomUUID();
		OutfitId outfitId = OutfitId.of("prison");
		SkinPropertyData property = new SkinPropertyData("value", "signature");

		try (OutfitStorage storage = new OutfitStorage(folder.resolve("outfits.db"))) {
			storage.saveGeneratedSkin(playerId, new GeneratedOutfitSkin(
				outfitId,
				OutfitRenderMode.COMPOSITE,
				SkinModel.CLASSIC,
				"original-hash",
				"template-a",
				property,
				100L
			));

			assertTrue(storage.generatedSkin(
				playerId,
				outfitId,
				OutfitRenderMode.COMPOSITE,
				SkinModel.CLASSIC,
				"original-hash",
				"template-b"
			).isEmpty());
			assertEquals(property, storage.generatedSkin(
				playerId,
				outfitId,
				OutfitRenderMode.COMPOSITE,
				SkinModel.CLASSIC,
				"original-hash",
				"template-a"
			).orElseThrow().property());
		}
	}
}
