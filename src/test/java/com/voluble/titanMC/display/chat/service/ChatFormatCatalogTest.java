package com.voluble.titanMC.display.chat.service;

import com.voluble.titanMC.display.chat.model.ChatFormat;
import com.voluble.titanMC.display.chat.model.ChatFormatId;
import com.voluble.titanMC.display.chat.model.ChatFormatSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatFormatCatalogTest {

	@Test
	void selectFallsBackToDefaultWhenNothingMatches() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat staff = format("staff", "titanmc.chat.staff", 100);
		ChatFormatCatalog catalog = new ChatFormatCatalog(fallback, List.of(staff));

		assertSame(fallback, catalog.selectFor(perm -> false));
	}

	@Test
	void selectChoosesHighestWeightMatching() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat donor = format("donor", "titanmc.chat.donor", 50);
		ChatFormat staff = format("staff", "titanmc.chat.staff", 100);
		ChatFormatCatalog catalog = new ChatFormatCatalog(fallback, List.of(donor, staff));

		Predicate<String> staffPlayer = Set.of("titanmc.chat.donor", "titanmc.chat.staff")::contains;
		Predicate<String> donorPlayer = Set.of("titanmc.chat.donor")::contains;

		assertSame(staff, catalog.selectFor(staffPlayer));
		assertSame(donor, catalog.selectFor(donorPlayer));
	}

	@Test
	void permissionlessFormatWithHigherWeightOverridesDefault() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat seasonal = format("seasonal", "", 10);
		ChatFormatCatalog catalog = new ChatFormatCatalog(fallback, List.of(seasonal));

		assertSame(seasonal, catalog.selectFor(perm -> false));
	}

	@Test
	void findByIdLocatesDefaultAndAdditional() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat staff = format("staff", "titanmc.chat.staff", 100);
		ChatFormatCatalog catalog = new ChatFormatCatalog(fallback, List.of(staff));

		assertSame(fallback, catalog.findById(ChatFormatId.of("default")).orElseThrow());
		assertSame(staff, catalog.findById(ChatFormatId.of("staff")).orElseThrow());
	}

	@Test
	void duplicateIdsAreRejected() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat first = format("staff", "titanmc.chat.staff", 50);
		ChatFormat second = format("staff", "titanmc.chat.staff", 100);

		assertThrows(IllegalArgumentException.class,
			() -> new ChatFormatCatalog(fallback, List.of(first, second)));
	}

	@Test
	void additionalCannotReuseDefaultId() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat collider = format("default", "titanmc.chat.staff", 100);

		assertThrows(IllegalArgumentException.class,
			() -> new ChatFormatCatalog(fallback, List.of(collider)));
	}

	@Test
	void allIncludesDefaultInInsertionOrder() {
		ChatFormat fallback = format("default", "", 0);
		ChatFormat donor = format("donor", "titanmc.chat.donor", 50);
		ChatFormat staff = format("staff", "titanmc.chat.staff", 100);
		ChatFormatCatalog catalog = new ChatFormatCatalog(fallback, List.of(donor, staff));

		assertEquals(List.of(fallback, donor, staff), catalog.all());
	}

	private static ChatFormat format(String id, String permission, int weight) {
		return new ChatFormat(
			ChatFormatId.of(id), permission, weight,
			ChatFormatSegment.EMPTY,
			ChatFormatSegment.text("<displayname>"),
			ChatFormatSegment.EMPTY,
			ChatFormatSegment.text("<gray> » </gray><message>")
		);
	}
}
