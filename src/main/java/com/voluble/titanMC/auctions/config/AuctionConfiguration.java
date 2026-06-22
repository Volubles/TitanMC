package com.voluble.titanMC.auctions.config;

import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuctionConfiguration(
	long minimumPrice,
	long maximumPrice,
	long saleDurationMillis,
	long claimDurationMillis,
	Map<WardId, RankId> minimumRanksByWard,
	Material chestMaterial,
	Material signMaterial,
	List<String> forSaleSign,
	List<String> claimedSign,
	List<String> publicSign
) {
	public AuctionConfiguration {
		if (minimumPrice < 0 || maximumPrice < minimumPrice || maximumPrice == Long.MAX_VALUE) {
			throw new IllegalArgumentException("invalid auction price range");
		}
		if (saleDurationMillis < 1000 || claimDurationMillis < 1000) throw new IllegalArgumentException("auction durations must be positive");
		minimumRanksByWard = Map.copyOf(minimumRanksByWard);
		if (!(chestMaterial.createBlockData() instanceof org.bukkit.block.data.type.Chest)) {
			throw new IllegalArgumentException("blocks.chest must be a chest block");
		}
		if (!(signMaterial.createBlockData() instanceof org.bukkit.block.data.type.WallSign)) {
			throw new IllegalArgumentException("blocks.sign must be a wall sign block");
		}
		forSaleSign = lines(forSaleSign, "for-sale");
		claimedSign = lines(claimedSign, "claimed");
		publicSign = lines(publicSign, "public");
	}

	public static AuctionConfiguration load(FileConfiguration yaml) {
		Material chest = material(yaml.getString("blocks.chest", "CHEST"));
		Material sign = material(yaml.getString("blocks.sign", "OAK_WALL_SIGN"));
		return new AuctionConfiguration(
			yaml.getLong("price.minimum", 500),
			yaml.getLong("price.maximum", 5000),
			yaml.getLong("timers.sale-seconds", 172800) * 1000L,
			yaml.getLong("timers.claim-seconds", 300) * 1000L,
			minimumRanks(yaml),
			chest,
			sign,
			yaml.getStringList("signs.for-sale"),
			yaml.getStringList("signs.claimed"),
			yaml.getStringList("signs.public")
		);
	}

	private static Map<WardId, RankId> minimumRanks(FileConfiguration yaml) {
		Map<WardId, RankId> requirements = new LinkedHashMap<>();
		ConfigurationSection section = yaml.getConfigurationSection("purchase-requirements.minimum-rank-by-ward");
		if (section == null) {
			requirements.put(WardId.of("e"), RankId.of("e3"));
			requirements.put(WardId.of("d"), RankId.of("d3"));
			requirements.put(WardId.of("c"), RankId.of("c3"));
			requirements.put(WardId.of("b"), RankId.of("b3"));
			requirements.put(WardId.of("a"), RankId.of("a3"));
			return requirements;
		}
		for (String ward : section.getKeys(false)) {
			String rank = section.getString(ward);
			if (rank == null || rank.isBlank()) {
				throw new IllegalArgumentException("Missing minimum auction rank for ward " + ward);
			}
			requirements.put(WardId.of(ward), RankId.of(rank));
		}
		return requirements;
	}

	private static Material material(String value) {
		Material material = Material.matchMaterial(value);
		if (material == null || !material.isBlock()) throw new IllegalArgumentException("invalid auction block: " + value);
		return material;
	}

	private static List<String> lines(List<String> value, String key) {
		if (value.size() != 4) throw new IllegalArgumentException("signs." + key + " must contain four lines");
		return List.copyOf(value);
	}
}
