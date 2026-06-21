package com.voluble.titanMC;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.donatortools.DonatorToolsService;
import com.voluble.titanMC.donatortools.command.DonatorToolsCommandModule;
import com.voluble.titanMC.donatortools.config.DonatorToolsConfigurationManager;
import com.voluble.titanMC.managers.EconomyManager;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.mines.protection.MineProtectionPolicy;
import com.voluble.titanMC.mines.regions.MineRegionException;
import com.voluble.titanMC.mines.regions.MineRegionService;
import com.voluble.titanMC.mines.reset.MineScheduler;
import com.voluble.titanMC.mines.listeners.MineBlockListener;
import com.voluble.titanMC.regions.persistence.RegionStorageException;
import com.voluble.titanMC.regions.protection.bukkit.BlockProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BlockAutomationProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BlockLifecycleProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BucketProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BukkitProtectionConfiguration;
import com.voluble.titanMC.regions.protection.bukkit.ExplosionProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.EntityContainerProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.EntityInteractionProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.BukkitProtectionBypass;
import com.voluble.titanMC.regions.command.RegionCommandModule;
import com.voluble.titanMC.regions.protection.bukkit.FireProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.FluidFlowProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.HangingProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.MobGriefProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.MobSpawnProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.PistonProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.PortalProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.TrustedFluidFlow;
import com.voluble.titanMC.regions.protection.bukkit.VehicleProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.RegionEntryProtectionListener;
import com.voluble.titanMC.regions.protection.bukkit.VaultRegionGroupProvider;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import com.voluble.titanMC.regions.protection.service.RegionEntryService;
import com.voluble.titanMC.regions.service.RegionEngine;
import io.voluble.michellelib.commands.CommandKit;
import io.voluble.michellelib.menu.MenuService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.voluble.titanMC.mines.command.MineCommandModule;

public final class TitanMC extends JavaPlugin {

	private static TitanMC instance;
	private ConfigManager configManager;
	private DonatorToolsConfigurationManager donatorToolsConfiguration;
	private DonatorToolsService donatorTools;
	private EconomyManager economyManager;
	private MineManager mineManager;
	private MineScheduler mineScheduler;
	private MenuService menuService;
	private RegionEngine regionEngine;
	private ProtectionService protectionService;
	private RegionGroupProvider regionGroups = RegionGroupProvider.none();

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
		donatorToolsConfiguration = new DonatorToolsConfigurationManager(this);
		try {
			configManager.registerComponent(donatorToolsConfiguration);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			getLogger().severe("Invalid donator tools configuration: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Mines
		MineManager loadedMineManager = new MineManager(this, new MineRegionService(regionEngine));
		try {
			loadedMineManager.load();
		} catch (MineRegionException exception) {
			getLogger().severe("Failed to synchronize mine regions: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		mineManager = loadedMineManager;
		getLogger().info("Loaded " + mineManager.getAll().size() + " mines");
		mineScheduler = new MineScheduler(this, mineManager);
		mineScheduler.start();
		getServer().getPluginManager().registerEvents(new MineBlockListener(this), this);
		getLogger().info("MineBlockListener registered");
		donatorTools = new DonatorToolsService(
			this,
			donatorToolsConfiguration,
			mineManager,
			mineScheduler,
			protectionService
		);

		// MichelleLib commands (dtools, mine)
		new CommandKit(this)
			.addModule(new DonatorToolsCommandModule(donatorTools))
			.addModule(new MineCommandModule(this))
			.addModule(new RegionCommandModule(this))
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

		ProtectionBypass protectionBypass = BukkitProtectionBypass.permission(
			getServer(), configuration.bypassPermission()
		);
		RegisteredServiceProvider<Permission> permissionRegistration =
			getServer().getServicesManager().getRegistration(Permission.class);
		if (permissionRegistration != null
			&& permissionRegistration.getProvider() != null
			&& permissionRegistration.getProvider().isEnabled()
			&& permissionRegistration.getProvider().hasGroupSupport()) {
			regionGroups = new VaultRegionGroupProvider(
				getServer(), permissionRegistration.getProvider()
			);
			getLogger().info(
				"Titan region group scopes enabled through " + permissionRegistration.getProvider().getName()
			);
		} else {
			regionGroups = RegionGroupProvider.none();
			getLogger().warning(
				"No Vault permissions provider with group support was found; group-scoped region rules will not match"
			);
		}
		protectionService = ProtectionService.forEngine(
			regionEngine,
			RegionPolicyRegistry.builder()
				.register(new MineProtectionPolicy())
				.build(),
			configuration.defaults(),
			protectionBypass,
			regionGroups
		);
		getServer().getPluginManager().registerEvents(
			new RegionEntryProtectionListener(
				new RegionEntryService(regionEngine, protectionBypass, regionGroups)
			),
			this
		);
		getServer().getPluginManager().registerEvents(new BlockProtectionListener(protectionService), this);
		TrustedFluidFlow trustedFluidFlow = new TrustedFluidFlow();
		getServer().getPluginManager().registerEvents(new BucketProtectionListener(protectionService, trustedFluidFlow), this);
		getServer().getPluginManager().registerEvents(new FluidFlowProtectionListener(protectionService, trustedFluidFlow), this);
		getServer().getPluginManager().registerEvents(new FireProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new BlockAutomationProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new BlockLifecycleProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new MobGriefProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new MobSpawnProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new PortalProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new ExplosionProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new PistonProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new EntityContainerProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new EntityInteractionProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new HangingProtectionListener(protectionService), this);
		getServer().getPluginManager().registerEvents(new VehicleProtectionListener(protectionService), this);
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
		if (mineManager != null) mineManager.close();
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

	// Economy Manager Delegates
	public Economy getEconomy() {
		return economyManager.getEconomy();
	}

	public MineManager getMineManager() { return mineManager; }
	public MineScheduler getMineScheduler() { return mineScheduler; }
	public MenuService getMenuService() { return menuService; }
	public RegionEngine getRegionEngine() { return regionEngine; }
	public RegionGroupProvider getRegionGroups() { return regionGroups; }
	public ProtectionService getProtectionService() { return protectionService; }
}
