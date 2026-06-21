package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class VaultRegionGroupProvider implements RegionGroupProvider {

	private final Server server;
	private final Permission permissions;

	public VaultRegionGroupProvider(Server server, Permission permissions) {
		this.server = Objects.requireNonNull(server, "server");
		this.permissions = Objects.requireNonNull(permissions, "permissions");
	}

	@Override
	public boolean isInGroup(ProtectionActor actor, WorldId worldId, String group) {
		if (actor.type() != ProtectionActor.Type.PLAYER || !permissions.hasGroupSupport()) return false;
		Player player = server.getPlayer(actor.playerId());
		World world = server.getWorld(worldId.value());
		return player != null
			&& world != null
			&& permissions.playerInGroup(world, player.getName(), group);
	}

	@Override
	public List<String> groups() {
		if (!permissions.hasGroupSupport()) return List.of();
		return Arrays.stream(permissions.getGroups())
			.filter(Objects::nonNull)
			.map(group -> group.toLowerCase(Locale.ROOT))
			.distinct()
			.sorted()
			.toList();
	}
}
