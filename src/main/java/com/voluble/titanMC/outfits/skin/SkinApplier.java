package com.voluble.titanMC.outfits.skin;

import org.bukkit.entity.Player;

public interface SkinApplier {
	static SkinApplier unavailable() {
		return new SkinApplier() {
			@Override
			public boolean available() {
				return false;
			}

			@Override
			public void apply(Player player, SkinPropertyData property) throws SkinApplyException {
				throw new SkinApplyException("SkinsRestorer is not installed or enabled");
			}

			@Override
			public void applyOriginal(Player player) throws SkinApplyException {
				throw new SkinApplyException("SkinsRestorer is not installed or enabled");
			}
		};
	}

	boolean available();

	void apply(Player player, SkinPropertyData property) throws SkinApplyException;

	void applyOriginal(Player player) throws SkinApplyException;
}
