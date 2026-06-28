package com.voluble.titanMC.onboarding.readiness;

import org.bukkit.entity.Player;

public interface OnboardingResourcePackSender {
	boolean available();

	void send(Player player);

	static OnboardingResourcePackSender unavailable() {
		return new OnboardingResourcePackSender() {
			@Override
			public boolean available() {
				return false;
			}

			@Override
			public void send(Player player) {
				throw new IllegalStateException("Nexo is not available");
			}
		};
	}
}
