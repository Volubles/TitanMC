package com.voluble.titanMC.cells.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellsConfigurationTest {
	@Test
	void emptyDatabaseCleanupRequiresExplicitConfirmation() {
		CellsConfiguration configuration = CellsConfiguration.load(defaultConfiguration());

		assertFalse(configuration.confirmEmptyDatabaseRegionCleanup());
	}

	@Test
	void cleanupConfirmationCanBeEnabledForOneRecoveryStartup() {
		YamlConfiguration yaml = defaultConfiguration();
		yaml.set("recovery.confirm-empty-database-region-cleanup", true);

		assertTrue(CellsConfiguration.load(yaml).confirmEmptyDatabaseRegionCleanup());
	}

	private static YamlConfiguration defaultConfiguration() {
		var source = CellsConfigurationTest.class.getClassLoader().getResourceAsStream("cells.yml");
		if (source == null) throw new IllegalStateException("Missing cells.yml test resource");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(source, StandardCharsets.UTF_8));
	}
}
