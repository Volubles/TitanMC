package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.model.CredSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredSourceRegistryTest {
	@Test
	void registerExposesDisplayNameAndEnabledFlag() {
		CredSourceRegistry registry = new CredSourceRegistry();
		CredSource mining = CredSource.of("mining");
		registry.register(mining, "Mining", true);

		assertTrue(registry.isRegistered(mining));
		assertTrue(registry.isEnabled(mining));
		assertEquals("Mining", registry.displayName(mining).orElseThrow());
	}

	@Test
	void unregisteredSourcesReadAsDisabled() {
		CredSourceRegistry registry = new CredSourceRegistry();
		CredSource ghost = CredSource.of("ghost");

		assertFalse(registry.isRegistered(ghost));
		assertFalse(registry.isEnabled(ghost));
		assertTrue(registry.displayName(ghost).isEmpty());
	}

	@Test
	void reRegisterOverridesPreviousEntry() {
		CredSourceRegistry registry = new CredSourceRegistry();
		CredSource mining = CredSource.of("mining");
		registry.register(mining, "Mining", true);
		registry.register(mining, "Mining (paused)", false);

		assertFalse(registry.isEnabled(mining));
		assertEquals("Mining (paused)", registry.displayName(mining).orElseThrow());
	}

	@Test
	void rejectsBlankDisplayName() {
		CredSourceRegistry registry = new CredSourceRegistry();
		assertThrows(IllegalArgumentException.class,
			() -> registry.register(CredSource.of("mining"), "  ", true));
	}

	@Test
	void registeredPreservesInsertionOrder() {
		CredSourceRegistry registry = new CredSourceRegistry();
		registry.register(CredSource.of("mining"), "Mining", true);
		registry.register(CredSource.of("woodcutting"), "Woodcutting", true);
		registry.register(CredSource.of("admin"), "Admin", true);

		assertEquals(java.util.List.of(
			CredSource.of("mining"),
			CredSource.of("woodcutting"),
			CredSource.of("admin")
		), java.util.List.copyOf(registry.registered()));
	}
}
