package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;

import java.util.Objects;
import java.util.UUID;

public sealed interface RankupResult {

	record Success(PlayerRank previous, PlayerRank current, long charged) implements RankupResult {
		public Success {
			Objects.requireNonNull(previous, "previous");
			Objects.requireNonNull(current, "current");
			if (charged < 0) throw new IllegalArgumentException("charged must not be negative");
		}
	}

	record AtMaxRank(PlayerRank current) implements RankupResult {
		public AtMaxRank {
			Objects.requireNonNull(current, "current");
		}
	}

	record MissingRequirement(PrisonRank next, RankId required) implements RankupResult {
		public MissingRequirement {
			Objects.requireNonNull(next, "next");
			Objects.requireNonNull(required, "required");
		}
	}

	record InsufficientFunds(PrisonRank next, long needed, double balance) implements RankupResult {
		public InsufficientFunds {
			Objects.requireNonNull(next, "next");
			if (needed < 0) throw new IllegalArgumentException("needed must not be negative");
		}
	}

	record EconomyUnavailable() implements RankupResult {
	}

	record NoCurrentRank(UUID playerId) implements RankupResult {
		public NoCurrentRank {
			Objects.requireNonNull(playerId, "playerId");
		}
	}
}
