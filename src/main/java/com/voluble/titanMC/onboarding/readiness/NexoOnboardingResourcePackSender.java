package com.voluble.titanMC.onboarding.readiness;

import com.nexomc.nexo.NexoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class NexoOnboardingResourcePackSender implements OnboardingResourcePackSender {
	@Override
	public boolean available() {
		return Bukkit.getPluginManager().isPluginEnabled("Nexo");
	}

	@Override
	public void send(Player player) {
		NexoPlugin.instance().packServer().sendPack(player);
	}
}
