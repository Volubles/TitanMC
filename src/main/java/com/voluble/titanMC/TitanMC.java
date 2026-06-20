package com.voluble.titanMC;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.donatorTools.DonatorToolsCommandModule;
import com.voluble.titanMC.donatorTools.tools.DonatorToolsConfigManager;
import com.voluble.titanMC.managers.EconomyManager;
import com.voluble.titanMC.managers.RegistrationManager;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.mines.reset.MineScheduler;
import com.voluble.titanMC.mines.listeners.MineBlockListener;
import com.voluble.titanMC.regions.persistence.RegionStorageException;
import com.voluble.titanMC.regions.protection.bukkit.BlockProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BukkitProtectionConfiguration;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import com.voluble.titanMC.regions.service.RegionEngine;
import io.voluble.michellelib.commands.CommandKit;
import io.voluble.michellelib.menu.MenuService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import com.voluble.titanMC.mines.command.MineCommandModule;

public final class TitanMC extends JavaPlugin {

	private static TitanMC instance;
	private ConfigManager configManager;
	private DonatorToolsConfigManager donatorToolsConfigManager;
	private EconomyManager economyManager;
	private RegistrationManager registrationManager;
	private MineManager mineManager;
	private MineScheduler mineScheduler;
	private MenuService menuService;
	private RegionEngine regionEngine;
	private ProtectionService protectionService;

	@Override
	public void onEnable() {
		instance = this;
		try {
			regionEngine = RegionEngine.open(getDataFolder().toPath().resolve("regions.db"));
			getLogger().info("Titan Region Engine loaded " + regionEngine.snapshot().definitions().size() + " regions");
		} catch (RegionStorageException exception) {
			getLogger().severe("Titan Region Engine failed to initialize: " + exception.getMessage());
			exception.printStackTrace();
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Initialize menu service
		menuService = MenuService.create(this);

		// Initialize general config
		configManager = new ConfigManager(this);
		configManager.initialize();
		if (!initializeProtection()) return;

		// Register component configs
		donatorToolsConfigManager = new DonatorToolsConfigManager(this);
		configManager.registerComponent(donatorToolsConfigManager);

		// Events
		registrationManager = new RegistrationManager(this);
		registrationManager.registerEvents();

		// Mines
		mineManager = new MineManager(this);
		mineManager.load();
		getLogger().info("Loaded " + mineManager.getAll().size() + " mines");
		mineScheduler = new MineScheduler(this, mineManager);
		mineScheduler.start();
		getServer().getPluginManager().registerEvents(new MineBlockListener(this), this);
		getLogger().info("MineBlockListener registered");

		// MichelleLib commands (dtools, mine)
		new CommandKit(this)
			.addModule(new DonatorToolsCommandModule())
			.addModule(new MineCommandModule(this))
			.install();

		getLogger().info("TitanMC has been enabled!");
	}

	private boolean initializeProtection() {
		BukkitProtectionConfiguration configuration;
		try {
			configuration = BukkitProtectionConfiguration.load(getConfig(), getServer());
		} catch (IllegalArgumentException exception) {
			getLogger().severe("Invalid protection configuration: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		if (!configuration.enabled()) {
			getLogger().info("Titan protection is disabled");
			return true;
		}

		protectionService = ProtectionService.forEngine(
			regionEngine,
			RegionPolicyRegistry.builder().build(),
			configuration.defaults(),
			ProtectionBypass.permission(configuration.bypassPermission())
		);
		getServer().getPluginManager().registerEvents(new BlockProtectionListener(protectionService), this);
		if (configuration.protectedWorlds().isEmpty()) {
			getLogger().warning("Titan protection is enabled, but protected-worlds is empty; no world is protected");
		} else {
			getLogger().info("Titan protection enabled for " + configuration.protectedWorlds().size() + " world(s)");
		}
		return true;
	}

	@Override
	public void onDisable() {
		if (menuService != null) menuService.shutdown();
		if (mineScheduler != null) mineScheduler.stop();
		if (mineManager != null) mineManager.saveAll();
		if (regionEngine != null) {
			try {
				regionEngine.close();
			} catch (RegionStorageException exception) {
				getLogger().severe("Failed to close Titan Region Engine cleanly: " + exception.getMessage());
			}
		}
	}

	public static TitanMC getInstance() {
		return instance;
	}

	// Donator Tools Config Delegates
	public void reloadDonatorToolsConfig() {
		donatorToolsConfigManager.reload();
	}

	public boolean isBlockAllowed(Material material) {
		return donatorToolsConfigManager.isBlockAllowed(material);
	}

	// Economy Manager Delegates
	public Economy getEconomy() {
		return economyManager.getEconomy();
	}

	public MineManager getMineManager() { return mineManager; }
	public MineScheduler getMineScheduler() { return mineScheduler; }
	public MenuService getMenuService() { return menuService; }
	public RegionEngine getRegionEngine() { return regionEngine; }
	public ProtectionService getProtectionService() { return protectionService; }
}
