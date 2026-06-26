package com.voluble.titanMC.progression.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionModelTest {
	@Test
	void credAmountRejectsNegative() {
		assertThrows(IllegalArgumentException.class, () -> new CredAmount(-1L));
	}

	@Test
	void credAmountZeroIsSingleton() {
		assertSame(CredAmount.ZERO, CredAmount.of(0L));
		assertTrue(CredAmount.ZERO.isZero());
	}

	@Test
	void credAmountPlusOverflowThrows() {
		CredAmount almost = CredAmount.of(Long.MAX_VALUE - 1);
		assertThrows(ArithmeticException.class, () -> almost.plus(CredAmount.of(10L)));
	}

	@Test
	void credSourceNormalizesAndAllowsNamespaces() {
		assertEquals("mining", CredSource.of(" Mining ").value());
		assertEquals("quest:warden_intake", CredSource.of("QUEST:warden_intake").value());
	}

	@Test
	void credSourceRejectsBadCharacters() {
		assertThrows(IllegalArgumentException.class, () -> CredSource.of("Mining Source"));
		assertThrows(IllegalArgumentException.class, () -> CredSource.of("_starts-bad"));
	}

	@Test
	void playerProgressionRejectsInvalidFields() {
		UUID id = UUID.randomUUID();
		assertThrows(IllegalArgumentException.class, () -> new PlayerProgression(id, -1L, 1, 0L));
		assertThrows(IllegalArgumentException.class, () -> new PlayerProgression(id, 0L, 0, 0L));
	}

	@Test
	void initialIsLevelOneZeroCred() {
		UUID id = UUID.randomUUID();
		PlayerProgression progression = PlayerProgression.initial(id, 1_000L);
		assertEquals(0L, progression.totalCred());
		assertEquals(1, progression.level());
		assertEquals(1_000L, progression.updatedAtEpochMillis());
	}
}
