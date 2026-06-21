package com.voluble.titanMC.ranks.config;

import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRankConfigurationTest {
	@Test
	void bundledConfigurationDefinesCompleteProgression() throws Exception {
		try (var source = getClass().getClassLoader().getResourceAsStream("ranks.yml")) {
			assertNotNull(source);
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
				new InputStreamReader(source, StandardCharsets.UTF_8)
			);
			var catalog = RankConfiguration.load(yaml).catalog();

			assertEquals(5, catalog.wards().size());
			assertEquals(20, catalog.ranks().size());
			assertEquals(RankId.of("e4"), catalog.ranks().getFirst().id());
			assertEquals(RankId.of("a1"), catalog.ranks().getLast().id());
			assertEquals(WardId.of("e"), catalog.requireRank(RankId.of("e3")).wardId());
			assertTrue(catalog.meetsRequirement(RankId.of("a1"), RankId.of("e4")));
		}
	}
}
