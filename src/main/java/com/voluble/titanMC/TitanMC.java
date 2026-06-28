package com.voluble.titanMC;

import com.voluble.titanMC.auctions.AuctionListener;
import com.voluble.titanMC.auctions.AuctionManagedBlockAccess;
import com.voluble.titanMC.auctions.AuctionService;
import com.voluble.titanMC.auctions.AuctionStorage;
import com.voluble.titanMC.auctions.command.AuctionCommandModule;
import com.voluble.titanMC.auctions.config.AuctionConfigurationManager;
import com.voluble.titanMC.cells.CellLeaseScheduler;
import com.voluble.titanMC.cells.CellManager;
import com.voluble.titanMC.cells.CellRentalService;
import com.voluble.titanMC.cells.CellResetService;
import com.voluble.titanMC.cells.CellSignService;
import com.voluble.titanMC.cells.CellTrackingListener;
import com.voluble.titanMC.cells.command.CellCommandModule;
import com.voluble.titanMC.cells.baseline.CellBaselineCaptureService;
import com.voluble.titanMC.cells.config.CellsConfigurationManager;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.cells.region.CellProtectionPolicy;
import com.voluble.titanMC.cells.region.CellRegionService;
import com.voluble.titanMC.cells.CellSignRenderer;
import com.voluble.titanMC.cells.CellManagementService;
import com.voluble.titanMC.cells.ui.CellMenuService;
import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.donatortools.DonatorToolsService;
import com.voluble.titanMC.donatortools.command.DonatorToolsCommandModule;
import com.voluble.titanMC.donatortools.config.DonatorToolsConfigurationManager;
import com.voluble.titanMC.display.message.DisplayBroadcastService;
import com.voluble.titanMC.display.dialogue.DialogueDefinition;
import com.voluble.titanMC.display.dialogue.DialogueDefinitionLoader;
import com.voluble.titanMC.display.dialogue.DialogueRenderer;
import com.voluble.titanMC.display.dialogue.DialogueScrollListener;
import com.voluble.titanMC.display.dialogue.DialogueService;
import com.voluble.titanMC.display.dialogue.DialogueTheme;
import com.voluble.titanMC.display.dialogue.DialogueThemeLoader;
import com.voluble.titanMC.display.dialogue.command.DialogueCommandModule;
import com.voluble.titanMC.display.dialogue.titan.TitanDialoguePack;
import com.voluble.titanMC.display.dialogue.titan.TitanDialogueResourcePackService;
import com.voluble.titanMC.managers.EconomyManager;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.mines.protection.MineProtectionPolicy;
import com.voluble.titanMC.mines.regions.MineRegionException;
import com.voluble.titanMC.mines.regions.MineRegionService;
import com.voluble.titanMC.mines.reset.MineScheduler;
import com.voluble.titanMC.mines.listeners.MineBlockListener;
import com.voluble.titanMC.mines.breaking.MineBlockAccess;
import com.voluble.titanMC.mines.breaking.MineBlockAccessListener;
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
import com.voluble.titanMC.regions.protection.bukkit.ManagedBlockAccessRegistry;
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
import com.voluble.titanMC.ranks.bukkit.PlayerRankListener;
import com.voluble.titanMC.ranks.command.RankCommandModule;
import com.voluble.titanMC.ranks.config.RankConfigurationManager;
import com.voluble.titanMC.ranks.persistence.PlayerRankStorage;
import com.voluble.titanMC.ranks.service.PlayerRankService;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.ranks.service.RankEconomy;
import com.voluble.titanMC.ranks.service.RankupService;
import com.voluble.titanMC.ranks.service.WardRankRequirements;
import com.voluble.titanMC.ranks.service.VaultRankEconomy;
import com.voluble.titanMC.util.ComponentFiles;
import io.voluble.michellelib.commands.CommandKit;
import io.voluble.michellelib.menu.MenuService;
import net.milkbowl.vault.economy.Economy;
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
	private CellManager cellManager;
	private CellLeaseScheduler cellLeaseScheduler;
	private CellSignRenderer cellSignRenderer;
	private CellsConfigurationManager cellsConfiguration;
	private AuctionConfigurationManager auctionConfiguration;
	private RankConfigurationManager rankConfiguration;
	private PlayerRankStorage rankStorage;
	private PlayerRankService rankService;
	private RankupService rankupService;
	private AuctionService auctionService;
	private ManagedBlockAccessRegistry managedBlockAccess;
	private CellBaselineCaptureService cellBaselineCapture;
	private DisplayBroadcastService displayBroadcastService;
	private DialogueService dialogueService;

	@Override
	public void onEnable() {
		instance = this;
		try {
			regionEngine = RegionEngine.open(ComponentFiles.resolveData(getDataFolder().toPath(), "regions", "regions.db"));
			getLogger().info("Titan Region Engine loaded " + regionEngine.snapshot().definitions().size() + " regions");
		} catch (RegionStorageException exception) {
			getLogger().severe("Titan Region Engine failed to initialize: " + exception.getMessage());
			exception.printStackTrace();
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Initialize menu service
		menuService = MenuService.create(this);
		displayBroadcastService = DisplayBroadcastService.create(getServer());
		TitanDialoguePack dialoguePack = new TitanDialogueResourcePackService(this).generate();
		getLogger().info("Generated TitanMC dialogue resource pack at " + dialoguePack.outputDirectory());
		DialogueTheme dialogueTheme = DialogueThemeLoader.titanDefault(this, dialoguePack);
		DialogueDefinition previewDialogue = DialogueDefinitionLoader.loadBundled(
			this,
			"display/dialogue/titan/Dialogues/default_example.yml",
			"preview",
			dialogueTheme
		);
		dialogueService = new DialogueService(this, new DialogueRenderer());
		getServer().getPluginManager().registerEvents(new DialogueScrollListener(dialogueService), this);

		// Initialize general config
		configManager = new ConfigManager(this);
		configManager.initialize();

		// Register component configs
		donatorToolsConfiguration = new DonatorToolsConfigurationManager(this);
		cellsConfiguration = new CellsConfigurationManager(this);
		auctionConfiguration = new AuctionConfigurationManager(this);
		rankConfiguration = new RankConfigurationManager(this);
		try {
			configManager.registerComponent(rankConfiguration);
			configManager.registerComponent(donatorToolsConfiguration);
			configManager.registerComponent(cellsConfiguration);
			configManager.registerComponent(auctionConfiguration);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			getLogger().severe("Invalid component configuration: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		getLogger().info(
			"Loaded " + rankConfiguration.catalog().ranks().size()
				+ " prison ranks across " + rankConfiguration.catalog().wards().size() + " wards"
		);
		economyManager = new EconomyManager(this);
		if (!economyManager.initialize()) getLogger().warning("No Vault economy provider found; cell renting is disabled");
		if (!initializeRanks()) return;
		managedBlockAccess = new ManagedBlockAccessRegistry(getLogger());
		if (!initializeProtection()) return;

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
		MineBlockAccess mineBlockAccess = new MineBlockAccess(mineManager);
		getServer().getPluginManager().registerEvents(new MineBlockAccessListener(mineBlockAccess), this);
		getServer().getPluginManager().registerEvents(new MineBlockListener(this), this);
		getLogger().info("MineBlockListener registered");
		donatorTools = new DonatorToolsService(
			this,
			donatorToolsConfiguration,
			mineManager,
			mineScheduler,
			protectionService,
			mineBlockAccess
		);

		WardRankRequirements cellEligibility;
		try {
			java.nio.file.Path cellDatabase = ComponentFiles.resolveData(
				getDataFolder().toPath(), "cells", "cells.db"
			);
			boolean existingCellDatabase = java.nio.file.Files.isRegularFile(cellDatabase);
			CellRegionService cellRegions = new CellRegionService(regionEngine);
			boolean confirmedEmptyCleanup = cellsConfiguration.current().confirmEmptyDatabaseRegionCleanup();
			if (!existingCellDatabase && cellRegions.hasManagedRegions() && !confirmedEmptyCleanup) {
				throw new IllegalStateException(
					"cells.db is missing while managed cell regions still exist. Restore the database or set "
						+ "recovery.confirm-empty-database-region-cleanup=true in cells.yml for one startup."
				);
			}
			cellManager = new CellManager(
				new CellStorage(cellDatabase),
				cellRegions,
				rankConfiguration.catalog()
			);
			cellManager.load();
			if (!existingCellDatabase && confirmedEmptyCleanup) {
				cellsConfiguration.consumeEmptyDatabaseRegionCleanupConfirmation();
				getLogger().warning("Consumed one-shot confirmation after reconciling orphaned cell regions");
			}
			cellEligibility = new WardRankRequirements(
				rankConfiguration.catalog(), cellsConfiguration.current().minimumRanksByWard()
			);
		} catch (Exception exception) {
			getLogger().severe("Failed to initialize Cells: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		CellResetService cellResets = new CellResetService(this, cellManager);
		cellBaselineCapture = new CellBaselineCaptureService(this);
		cellSignRenderer = new CellSignRenderer(this, cellManager, cellsConfiguration);
		cellResets.stateListener(cellSignRenderer::refresh);
		CellRentalService cellRentals = new CellRentalService(
			this, cellManager, economyManager.getEconomy(), cellSignRenderer, rankService, cellEligibility
		);
		CellManagementService cellManagement = new CellManagementService(this, cellManager, cellResets, cellSignRenderer, cellsConfiguration, economyManager.getEconomy());
		CellSignService cellSigns = new CellSignService(this, cellManager, cellSignRenderer);
		CellMenuService cellMenus = new CellMenuService(this, menuService, cellManager, cellRentals, cellManagement, cellsConfiguration);
		cellSigns.menus(cellMenus);
		getServer().getPluginManager().registerEvents(new CellTrackingListener(cellManager), this);
		getServer().getPluginManager().registerEvents(cellSigns, this);
		getServer().getPluginManager().registerEvents(cellMenus, this);
		cellSignRenderer.start();
		cellResets.resume();
		cellLeaseScheduler = new CellLeaseScheduler(this, cellManager, cellResets);
		cellLeaseScheduler.start();
		try {
			auctionService = new AuctionService(
				this,
				new AuctionStorage(ComponentFiles.resolveData(getDataFolder().toPath(), "auctions", "auctions.db")),
				cellManager.storage(),
				auctionConfiguration,
				economyManager.getEconomy(),
				rankConfiguration.catalog(),
				rankService
			);
			auctionService.start();
			managedBlockAccess.register(new AuctionManagedBlockAccess(auctionService));
			getServer().getPluginManager().registerEvents(new AuctionListener(this, auctionService), this);
		} catch (Exception exception) {
			getLogger().severe("Failed to initialize Auctions: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// MichelleLib commands (dtools, mine)
		new CommandKit(this)
			.addModule(new DonatorToolsCommandModule(donatorTools))
			.addModule(new MineCommandModule(this))
			.addModule(new RegionCommandModule(this))
			.addModule(new CellCommandModule(
				cellManager, cellResets, cellSigns, cellSignRenderer, rankConfiguration.catalog(), cellBaselineCapture
			))
			.addModule(new AuctionCommandModule(auctionService, rankConfiguration.catalog()))
			.addModule(new RankCommandModule(rankConfiguration.catalog(), rankService, rankupService))
			.addModule(new DialogueCommandModule(dialogueService, previewDialogue))
			.install();

		getLogger().info("TitanMC has been enabled!");
	}

	private boolean initializeRanks() {
		try {
			rankStorage = new PlayerRankStorage(
				ComponentFiles.resolveData(getDataFolder().toPath(), "ranks", "players.db")
			);
		} catch (java.sql.SQLException exception) {
			getLogger().severe("Failed to open rank storage: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		RankEconomy rankEconomy = economyManager.isEnabled()
			? new VaultRankEconomy(economyManager.getEconomy(), getServer())
			: RankEconomy.unavailable();
		rankService = new PlayerRankService(
			rankConfiguration.catalog(),
			rankStorage,
			event -> getServer().getPluginManager().callEvent(event),
			getLogger()
		);
		try {
			rankService.load();
		} catch (java.sql.SQLException exception) {
			getLogger().severe("Failed to load player ranks: " + exception.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		rankupService = new RankupService(rankConfiguration.catalog(), rankService, rankEconomy, getLogger());
		getServer().getPluginManager().registerEvents(new PlayerRankListener(rankService), this);
		getLogger().info("Player ranks ready (" + (rankEconomy.available() ? "Vault economy" : "no economy") + ")");
		return true;
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
		if (getServer().getPluginManager().isPluginEnabled("Vault")) {
			regionGroups = VaultRegionGroupProvider.create(getServer());
			if (regionGroups instanceof VaultRegionGroupProvider) {
				getLogger().info("Titan region group scopes enabled through Vault");
			} else {
				getLogger().warning(
					"Vault has no enabled permissions provider with group support; group-scoped region rules will not match"
				);
			}
		} else {
			regionGroups = RegionGroupProvider.none();
			getLogger().warning(
				"Vault is not installed; group-scoped region rules will not match"
			);
		}
		protectionService = ProtectionService.forEngine(
			regionEngine,
			RegionPolicyRegistry.builder()
				.register(new MineProtectionPolicy())
				.register(new CellProtectionPolicy())
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
		getServer().getPluginManager().registerEvents(
			new BlockProtectionListener(protectionService, managedBlockAccess),
			this
		);
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
		if (dialogueService != null) dialogueService.close();
		if (menuService != null) menuService.shutdown();
		if (mineScheduler != null) mineScheduler.stop();
		if (cellLeaseScheduler != null) cellLeaseScheduler.stop();
		if (cellSignRenderer != null) cellSignRenderer.stop();
		if (cellBaselineCapture != null) cellBaselineCapture.close();
		if (auctionService != null) {
			try { auctionService.close(); }
			catch (Exception exception) { getLogger().severe("Failed to close Auctions cleanly: " + exception.getMessage()); }
		}
		if (mineManager != null) mineManager.close();
		if (cellManager != null) {
			try { cellManager.close(); }
			catch (Exception exception) { getLogger().severe("Failed to close Cells cleanly: " + exception.getMessage()); }
		}
		if (rankStorage != null) {
			try { rankStorage.close(); }
			catch (Exception exception) { getLogger().severe("Failed to close Ranks cleanly: " + exception.getMessage()); }
		}
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
	public CellManager getCellManager() { return cellManager; }
	public RankCatalog getRanks() { return rankConfiguration.catalog(); }
	public DisplayBroadcastService getDisplayBroadcastService() { return displayBroadcastService; }
}
