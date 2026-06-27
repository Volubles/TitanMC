package com.voluble.titanMC.display.notice;

import java.util.ArrayList;
import java.util.List;

public final class MessageDefaults {
	private static final List<MessageDefinition> ALL = new ArrayList<>();

	public static final MessageDefinition COMMAND_PLAYER_ONLY = message(
		"command.player-only", MessageType.ERROR, "Only players can use this command."
	);
	public static final MessageDefinition COMMAND_UNKNOWN_PLAYER = message(
		"command.unknown-player", MessageType.ERROR, "Unknown player. Use a cached name or UUID."
	);
	public static final MessageDefinition COMMAND_RUNTIME_ERROR = message(
		"command.runtime-error", MessageType.ERROR, "{{reason}}"
	);

	public static final MessageDefinition DONATOR_TOOLS_HELP_TITLE = message(
		"donator-tools.help.title", MessageType.INFO, "Donator Tools"
	);
	public static final MessageDefinition DONATOR_TOOLS_HELP_USAGE = lines(
		"donator-tools.help.usage", MessageType.INFO,
		"/dtools <tool> [player]",
		"/dtools reload"
	);
	public static final MessageDefinition DONATOR_TOOLS_HELP_TOOL = message(
		"donator-tools.help.tool", MessageType.INFO, "{{tool}} - {{description}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOAD_DENIED = message(
		"donator-tools.reload.denied", MessageType.ERROR, "You may not reload donator tools."
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOADED = message(
		"donator-tools.reload.success", MessageType.SUCCESS, "Donator tools reloaded."
	);
	public static final MessageDefinition DONATOR_TOOLS_RELOAD_FAILED = message(
		"donator-tools.reload.failed", MessageType.ERROR, "Donator tools reload failed: {{reason}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_GIVE_DENIED = message(
		"donator-tools.give.denied", MessageType.ERROR, "You may not give donator tools."
	);
	public static final MessageDefinition DONATOR_TOOLS_UNKNOWN = message(
		"donator-tools.unknown", MessageType.ERROR, "Unknown tool. Available: {{tools}}"
	);
	public static final MessageDefinition DONATOR_TOOLS_INVENTORY_FULL = message(
		"donator-tools.inventory-full", MessageType.INFO,
		"Your inventory was full, so the {{tool}} was dropped."
	);
	public static final MessageDefinition DONATOR_TOOLS_RECEIVED = message(
		"donator-tools.received", MessageType.SUCCESS, "You received a {{tool}}."
	);
	public static final MessageDefinition DONATOR_TOOLS_GAVE = message(
		"donator-tools.gave", MessageType.SUCCESS, "Gave {{tool}} to {{player}}."
	);

	public static final MessageDefinition CELLS_USAGE = message(
		"cells.usage", MessageType.INFO,
		"Usage: /cell <create|delete|list|info|displayname|ward|reset|baseline|sign|member>"
	);
	public static final MessageDefinition CELLS_CUBOID_REQUIRED = message(
		"cells.create.cuboid-required", MessageType.ERROR, "Cells must use a cuboid selection."
	);
	public static final MessageDefinition CELLS_BASELINE_CAPTURING = message(
		"cells.baseline.capturing", MessageType.INFO, "Capturing the cell baseline..."
	);
	public static final MessageDefinition CELLS_CREATE_FAILED = message(
		"cells.create.failed", MessageType.ERROR, "Cell creation failed: {{reason}}"
	);
	public static final MessageDefinition CELLS_CREATED = message(
		"cells.create.success", MessageType.SUCCESS, "Created cell '{{cell}}' in {{ward}} ward."
	);
	public static final MessageDefinition CELLS_DELETED = message(
		"cells.delete.success", MessageType.SUCCESS, "Deleted cell."
	);
	public static final MessageDefinition CELLS_LIST = message(
		"cells.list", MessageType.INFO, "Cells: {{cells}}"
	);
	public static final MessageDefinition CELLS_UNKNOWN = message(
		"cells.unknown", MessageType.ERROR, "Unknown cell."
	);
	public static final MessageDefinition CELLS_INFO = message(
		"cells.info", MessageType.INFO,
		"{{display_name}} ({{cell}}) | ${{price}} | ward {{ward}} | duration {{duration}}s | maximum {{maximum}}s | {{state}}"
	);
	public static final MessageDefinition CELLS_WARD_MOVED = message(
		"cells.ward.moved", MessageType.SUCCESS, "Moved cell '{{cell}}' to {{ward}} ward."
	);
	public static final MessageDefinition CELLS_DISPLAY_NAME_UPDATED = message(
		"cells.display-name.updated", MessageType.SUCCESS, "Updated cell display name."
	);
	public static final MessageDefinition CELLS_RESET_STARTED = message(
		"cells.reset.started", MessageType.SUCCESS, "Cell reset started."
	);
	public static final MessageDefinition CELLS_BASELINE_NOT_AVAILABLE = message(
		"cells.baseline.not-available", MessageType.ERROR,
		"The cell must be available before its baseline can be updated."
	);
	public static final MessageDefinition CELLS_BASELINE_CAPTURE_FAILED = message(
		"cells.baseline.capture-failed", MessageType.ERROR, "Baseline capture failed: {{reason}}"
	);
	public static final MessageDefinition CELLS_BASELINE_UPDATED = message(
		"cells.baseline.updated", MessageType.SUCCESS, "Updated the baseline for cell '{{cell}}'."
	);
	public static final MessageDefinition CELLS_BASELINE_UPDATE_FAILED = message(
		"cells.baseline.update-failed", MessageType.ERROR, "Baseline update failed: {{reason}}"
	);
	public static final MessageDefinition CELLS_MEMBER_CHANGED = message(
		"cells.members.changed", MessageType.SUCCESS, "{{action}} {{player}} {{direction}} the cell."
	);
	public static final MessageDefinition CELLS_MEMBERS_LIST = message(
		"cells.members.list", MessageType.INFO, "Members: {{members}}"
	);
	public static final MessageDefinition CELLS_SIGN_LOOK = message(
		"cells.sign.look", MessageType.ERROR, "Look at a sign within 6 blocks."
	);
	public static final MessageDefinition CELLS_SIGN_LINKED = message(
		"cells.sign.linked", MessageType.SUCCESS, "Rental sign linked."
	);
	public static final MessageDefinition CELLS_SIGN_INVALID = message(
		"cells.sign.invalid", MessageType.ERROR, "This cell sign is no longer valid."
	);
	public static final MessageDefinition CELLS_ECONOMY_UNAVAILABLE = message(
		"cells.economy-unavailable", MessageType.ERROR, "Economy is unavailable."
	);
	public static final MessageDefinition CELLS_RENTING_UNAVAILABLE = message(
		"cells.renting-unavailable", MessageType.ERROR,
		"Renting is unavailable because no economy provider is active."
	);
	public static final MessageDefinition CELLS_OPERATION_BUSY = message(
		"cells.operation-busy", MessageType.ERROR, "A cell operation is already running."
	);
	public static final MessageDefinition CELLS_MAX_DURATION = message(
		"cells.max-duration", MessageType.ERROR, "This cell cannot be rented beyond its maximum duration."
	);
	public static final MessageDefinition CELLS_NOT_ENOUGH_MONEY = message(
		"cells.not-enough-money", MessageType.ERROR, "You do not have enough money."
	);
	public static final MessageDefinition CELLS_PAYMENT_FAILED = message(
		"cells.payment-failed", MessageType.ERROR, "The payment failed."
	);
	public static final MessageDefinition CELLS_RENT_EXTENDED = message(
		"cells.rent-extended", MessageType.SUCCESS, "Rent extended."
	);
	public static final MessageDefinition CELLS_EXTENSION_REFUNDED = message(
		"cells.extension-refunded", MessageType.ERROR, "Extension failed; your payment was refunded."
	);
	public static final MessageDefinition CELLS_RETURN_STARTED = message(
		"cells.return-started", MessageType.SUCCESS, "Cell return started."
	);
	public static final MessageDefinition CELLS_OWNER_ONLY = message(
		"cells.owner-only", MessageType.ERROR, "Only the cell owner can do that."
	);
	public static final MessageDefinition CELLS_RANK_UNAVAILABLE = message(
		"cells.rank-unavailable", MessageType.ERROR, "Your prison rank is not available yet."
	);
	public static final MessageDefinition CELLS_RANK_REQUIRED = message(
		"cells.rank-required", MessageType.ERROR, "You need rank {{rank}} to rent a cell in ward {{ward}}."
	);
	public static final MessageDefinition CELLS_RENT_ALREADY_PROCESSING_PLAYER = message(
		"cells.rent.already-processing-player", MessageType.ERROR,
		"Another cell rental is already being processed for you."
	);
	public static final MessageDefinition CELLS_RENT_ALREADY_PROCESSING_CELL = message(
		"cells.rent.already-processing-cell", MessageType.ERROR, "This cell is currently being processed."
	);
	public static final MessageDefinition CELLS_RENTED = message(
		"cells.rented", MessageType.SUCCESS, "You rented {{cell}}."
	);
	public static final MessageDefinition CELLS_RENT_REFUNDED = message(
		"cells.rent-refunded", MessageType.ERROR, "The rental could not be completed; your payment was refunded."
	);
	public static final MessageDefinition CELLS_MENU_MEMBER_PROMPT = message(
		"cells.menu.member-prompt", MessageType.INFO, "Type the player's username in chat."
	);
	public static final MessageDefinition CELLS_MENU_MEMBER_NOT_FOUND = message(
		"cells.menu.member-not-found", MessageType.ERROR,
		"No player named '{{player}}' was found. Open the members menu and try again."
	);
	public static final MessageDefinition CELLS_MENU_MEMBER_ADDED = message(
		"cells.menu.member-added", MessageType.SUCCESS, "Added {{player}} to the cell."
	);

	public static final MessageDefinition AUCTIONS_USAGE = message(
		"auctions.usage", MessageType.INFO, "Usage: /auction position <add|remove|list|teleport>"
	);
	public static final MessageDefinition AUCTIONS_LOOK_AT_BLOCK = message(
		"auctions.position.look-at-block", MessageType.ERROR, "Look at the block where the auction chest should spawn."
	);
	public static final MessageDefinition AUCTIONS_POSITION_ADDED = message(
		"auctions.position.added", MessageType.SUCCESS,
		"Added auction position {{position}} in {{ward}} ward. The chest and sign will face you."
	);
	public static final MessageDefinition AUCTIONS_POSITION_REMOVED = message(
		"auctions.position.removed", MessageType.SUCCESS, "Auction position removed."
	);
	public static final MessageDefinition AUCTIONS_POSITIONS_EMPTY = message(
		"auctions.position.empty", MessageType.INFO, "No auction positions configured."
	);
	public static final MessageDefinition AUCTIONS_POSITIONS_HEADER = message(
		"auctions.position.header", MessageType.INFO, "Auction positions:"
	);
	public static final MessageDefinition AUCTIONS_POSITION_TELEPORTED = message(
		"auctions.position.teleported", MessageType.SUCCESS, "Teleported to auction position {{position}}."
	);
	public static final MessageDefinition AUCTIONS_BUY_USING_SIGN = message(
		"auctions.buy-using-sign", MessageType.INFO, "Buy this mystery chest using its sign."
	);
	public static final MessageDefinition AUCTIONS_RESERVED = message(
		"auctions.reserved", MessageType.ERROR, "This chest is temporarily reserved for its buyer."
	);
	public static final MessageDefinition AUCTIONS_ADMIN_CREATIVE_ONLY = message(
		"auctions.admin-creative-only", MessageType.ERROR,
		"Auction blocks can only be removed by an auction administrator in creative mode."
	);
	public static final MessageDefinition AUCTIONS_DISCARDED = message(
		"auctions.discarded", MessageType.SUCCESS, "Auction permanently discarded."
	);
	public static final MessageDefinition AUCTIONS_DISCARD_FAILED = message(
		"auctions.discard-failed", MessageType.ERROR, "The auction could not be removed. Check the server log."
	);
	public static final MessageDefinition AUCTIONS_DELIVERY_FULL = message(
		"auctions.delivery-full", MessageType.ERROR,
		"Your inventory is full. Reopen the auction after making space to receive your item."
	);
	public static final MessageDefinition AUCTIONS_NOT_AVAILABLE = message(
		"auctions.not-available", MessageType.ERROR, "This auction is not available to you."
	);
	public static final MessageDefinition AUCTIONS_ITEM_UNAVAILABLE = message(
		"auctions.item-unavailable", MessageType.ERROR, "That auction item is no longer available."
	);
	public static final MessageDefinition AUCTIONS_NO_LONGER_FOR_SALE = message(
		"auctions.no-longer-for-sale", MessageType.ERROR, "This auction is no longer for sale."
	);
	public static final MessageDefinition AUCTIONS_EXPIRED = message(
		"auctions.expired", MessageType.ERROR, "This auction has expired."
	);
	public static final MessageDefinition AUCTIONS_RANK_UNAVAILABLE = message(
		"auctions.rank-unavailable", MessageType.ERROR, "Your prison rank is not available yet."
	);
	public static final MessageDefinition AUCTIONS_RANK_REQUIRED = message(
		"auctions.rank-required", MessageType.ERROR, "You need rank {{rank}} to buy an auction in ward {{ward}}."
	);
	public static final MessageDefinition AUCTIONS_ECONOMY_UNAVAILABLE = message(
		"auctions.economy-unavailable", MessageType.ERROR, "The economy is unavailable."
	);
	public static final MessageDefinition AUCTIONS_NOT_ENOUGH_MONEY = message(
		"auctions.not-enough-money", MessageType.ERROR, "You do not have enough money."
	);
	public static final MessageDefinition AUCTIONS_PAYMENT_FAILED = message(
		"auctions.payment-failed", MessageType.ERROR, "The payment failed."
	);
	public static final MessageDefinition AUCTIONS_PURCHASE_REFUNDED = message(
		"auctions.purchase-refunded", MessageType.ERROR, "The purchase failed; your payment was refunded."
	);
	public static final MessageDefinition AUCTIONS_PURCHASE_REFUND_FAILED = message(
		"auctions.purchase-refund-failed", MessageType.ERROR,
		"The purchase and automatic refund failed. Contact an administrator."
	);
	public static final MessageDefinition AUCTIONS_PURCHASED = message(
		"auctions.purchased", MessageType.SUCCESS,
		"You bought the mystery chest. You have {{duration}} of exclusive access."
	);

	public static final MessageDefinition RANK_NO_RANK = message(
		"ranks.no-rank", MessageType.ERROR, "You do not have a rank yet."
	);
	public static final MessageDefinition RANK_OWN = message(
		"ranks.own", MessageType.INFO, "Your rank: {{rank}}"
	);
	public static final MessageDefinition RANK_NEXT = message(
		"ranks.next", MessageType.INFO, "Next rank: {{rank}} (${{cost}})"
	);
	public static final MessageDefinition RANK_PLAYER_NO_RANK = message(
		"ranks.player-no-rank", MessageType.ERROR, "{{player}} has no rank."
	);
	public static final MessageDefinition RANK_PLAYER_INFO = message(
		"ranks.player-info", MessageType.INFO, "{{player}} is rank {{rank}}"
	);
	public static final MessageDefinition RANK_INVALID_ID = message(
		"ranks.invalid-id", MessageType.ERROR, "Invalid rank id: {{reason}}"
	);
	public static final MessageDefinition RANK_UNKNOWN = message(
		"ranks.unknown", MessageType.ERROR, "Unknown rank: {{rank}}"
	);
	public static final MessageDefinition RANK_SET = message(
		"ranks.set", MessageType.SUCCESS, "Set {{player}} to {{rank}}."
	);
	public static final MessageDefinition RANKUP_SUCCESS = message(
		"ranks.rankup.success", MessageType.SUCCESS, "Ranked up to {{rank}} for ${{cost}}."
	);
	public static final MessageDefinition RANKUP_MAX = message(
		"ranks.rankup.max", MessageType.INFO, "You are already at the highest rank."
	);
	public static final MessageDefinition RANKUP_MISSING_REQUIREMENT = message(
		"ranks.rankup.missing-requirement", MessageType.ERROR,
		"You must hold {{required}} before reaching {{next}}."
	);
	public static final MessageDefinition RANKUP_INSUFFICIENT_FUNDS = message(
		"ranks.rankup.insufficient-funds", MessageType.ERROR,
		"You need ${{needed}} to reach {{rank}} (you have ${{balance}})."
	);
	public static final MessageDefinition RANKUP_ECONOMY_UNAVAILABLE = message(
		"ranks.rankup.economy-unavailable", MessageType.ERROR,
		"Rankups are unavailable because no economy provider is active."
	);
	public static final MessageDefinition RANKUP_SAVE_REFUNDED = message(
		"ranks.rankup.save-refunded", MessageType.ERROR, "The rankup could not be saved. Your payment was refunded."
	);
	public static final MessageDefinition RANKUP_SAVE_REFUND_FAILED = message(
		"ranks.rankup.save-refund-failed", MessageType.ERROR,
		"The rankup could not be saved and the automatic refund failed. Contact staff."
	);
	public static final MessageDefinition RANKUP_NO_CURRENT_RANK = message(
		"ranks.rankup.no-current-rank", MessageType.ERROR,
		"You do not have a rank yet; rejoin to receive the starter rank."
	);

	public static final MessageDefinition CRED_PLAYER_INFO = message(
		"cred.player-info", MessageType.INFO, "{{player}}: {{summary}}"
	);
	public static final MessageDefinition CRED_AMOUNT_POSITIVE = message(
		"cred.amount-positive", MessageType.ERROR, "Amount must be positive."
	);
	public static final MessageDefinition CRED_INVALID_SOURCE = message(
		"cred.invalid-source", MessageType.ERROR, "Invalid source id: {{reason}}"
	);
	public static final MessageDefinition CRED_UNCHANGED = message(
		"cred.unchanged", MessageType.INFO, "{{player}} was unchanged (already at the boundary)."
	);
	public static final MessageDefinition CRED_CHANGED = message(
		"cred.changed", MessageType.SUCCESS, "{{verb}} {{player}} {{amount}} cred{{level_change}} [{{source}}]"
	);

	public static final MessageDefinition MILESTONE_COMPLETED = message(
		"milestones.completed", MessageType.SUCCESS, "Milestone completed: {{milestone}}"
	);
	public static final MessageDefinition MILESTONES_RELOADED = message(
		"milestones.reloaded", MessageType.SUCCESS, "Milestones reloaded."
	);
	public static final MessageDefinition MILESTONES_PROGRESS_EMPTY = message(
		"milestones.progress.empty", MessageType.INFO, "{{player}} has no milestone progress."
	);
	public static final MessageDefinition MILESTONES_PROGRESS_HEADER = message(
		"milestones.progress.header", MessageType.INFO, "Milestone progress for {{player}}:"
	);
	public static final MessageDefinition MILESTONES_PROGRESS_ROW = message(
		"milestones.progress.row", MessageType.INFO, "{{track}}: {{amount}}"
	);
	public static final MessageDefinition MILESTONES_RESET = message(
		"milestones.reset", MessageType.SUCCESS, "Reset milestone progress for {{player}}."
	);

	public static final MessageDefinition OUTFITS_FIRST_JOIN_PROMPT = lines(
		"outfits.first-join-prompt", MessageType.INFO,
		"Want a server outfit? Use /outfit to choose one, or /outfit use original to keep your skin."
	);
	public static final MessageDefinition OUTFITS_HEADER = message(
		"outfits.header", MessageType.INFO, "Available outfits:"
	);
	public static final MessageDefinition OUTFITS_LIST_ENTRY = message(
		"outfits.list-entry", MessageType.INFO, "{{id}} - {{name}}"
	);
	public static final MessageDefinition OUTFITS_USE_HINT = message(
		"outfits.use-hint", MessageType.INFO, "Use /outfit use <id> or /outfit use original."
	);
	public static final MessageDefinition OUTFITS_RELOADED = message(
		"outfits.reloaded", MessageType.SUCCESS, "Outfits reloaded."
	);
	public static final MessageDefinition OUTFITS_STATUS = message(
		"outfits.status", MessageType.INFO, "Outfits enabled: {{enabled}}, configured outfits: {{outfits}}"
	);
	public static final MessageDefinition OUTFITS_APPLYING = message(
		"outfits.applying", MessageType.INFO, "Preparing outfit {{outfit}}. This may take a few seconds."
	);
	public static final MessageDefinition OUTFITS_APPLYING_ORIGINAL = message(
		"outfits.applying-original", MessageType.INFO, "Restoring your original skin."
	);
	public static final MessageDefinition OUTFITS_APPLIED = message(
		"outfits.applied", MessageType.SUCCESS, "Applied outfit {{outfit}}."
	);
	public static final MessageDefinition OUTFITS_ORIGINAL = message(
		"outfits.original", MessageType.SUCCESS, "Your original skin is active."
	);
	public static final MessageDefinition OUTFITS_DISABLED = message(
		"outfits.disabled", MessageType.ERROR, "Outfits are currently disabled."
	);
	public static final MessageDefinition OUTFITS_UNKNOWN = message(
		"outfits.unknown", MessageType.ERROR, "Unknown outfit: {{outfit}}"
	);
	public static final MessageDefinition OUTFITS_NO_MINESKIN_KEY = message(
		"outfits.no-mineskin-key", MessageType.ERROR, "MineSkin is not configured yet."
	);
	public static final MessageDefinition OUTFITS_NO_ORIGINAL_SKIN = message(
		"outfits.no-original-skin", MessageType.ERROR, "Could not read your current skin."
	);
	public static final MessageDefinition OUTFITS_SKINS_RESTORER_UNAVAILABLE = message(
		"outfits.skins-restorer-unavailable", MessageType.ERROR, "SkinsRestorer is not available."
	);
	public static final MessageDefinition OUTFITS_BUSY = message(
		"outfits.busy", MessageType.ERROR, "Your outfit is already being prepared."
	);
	public static final MessageDefinition OUTFITS_FAILED = message(
		"outfits.failed", MessageType.ERROR, "The outfit could not be applied. Check the server log."
	);

	public static final MessageDefinition MINES_LIST = message(
		"mines.list", MessageType.INFO, "Mines: {{mines}}"
	);
	public static final MessageDefinition MINES_NAME_INVALID = message(
		"mines.name-invalid", MessageType.ERROR, "{{reason}}"
	);
	public static final MessageDefinition MINES_ALREADY_EXISTS = message(
		"mines.already-exists", MessageType.ERROR, "Mine already exists."
	);
	public static final MessageDefinition MINES_CREATED = message(
		"mines.created", MessageType.SUCCESS, "Created mine '{{mine}}' from your WorldEdit selection."
	);
	public static final MessageDefinition MINES_UNKNOWN = message(
		"mines.unknown", MessageType.ERROR, "Unknown mine."
	);
	public static final MessageDefinition MINES_REDEFINED = message(
		"mines.redefined", MessageType.SUCCESS, "Redefined '{{mine}}' from your WorldEdit selection."
	);
	public static final MessageDefinition MINES_SELECTION_OVERLAP = message(
		"mines.selection-overlap", MessageType.ERROR, "That selection overlaps mine '{{mine}}'."
	);
	public static final MessageDefinition MINES_INTERVAL_SET = message(
		"mines.interval-set", MessageType.SUCCESS, "Set interval for '{{mine}}' to {{seconds}}s."
	);
	public static final MessageDefinition MINES_DEPLETION_SET = message(
		"mines.depletion-set", MessageType.SUCCESS, "Depletion auto-reset for '{{mine}}' set to {{percent}}%. (-1 disables)"
	);
	public static final MessageDefinition MINES_DEPLETION_UNAVAILABLE = message(
		"mines.depletion-unavailable", MessageType.ERROR,
		"Depletion reset is unavailable for this mine's reset and diggable-block configuration."
	);
	public static final MessageDefinition MINES_SAFE_SPAWN_SET = message(
		"mines.safe-spawn-set", MessageType.SUCCESS, "Set safe spawn for '{{mine}}'."
	);
	public static final MessageDefinition MINES_INVALID_MATERIAL = message(
		"mines.invalid-material", MessageType.ERROR, "Invalid material."
	);
	public static final MessageDefinition MINES_PALETTE_UPDATED = message(
		"mines.palette-updated", MessageType.SUCCESS, "Palette updated: {{material}}={{weight}}"
	);
	public static final MessageDefinition MINES_PALETTE_REMOVED = message(
		"mines.palette-removed", MessageType.SUCCESS, "Removed from palette: {{material}}"
	);
	public static final MessageDefinition MINES_FORCE_RESET = message(
		"mines.force-reset", MessageType.SUCCESS, "Force reset triggered for '{{mine}}'."
	);
	public static final MessageDefinition MINES_DELETED = message(
		"mines.deleted", MessageType.SUCCESS, "Deleted mine '{{mine}}'. The blocks were left untouched."
	);
	public static final MessageDefinition MINES_TEMPLATE_CAPTURE_ACTIVE = message(
		"mines.template.capture-active", MessageType.SUCCESS, "{{result}} Template reset is now active."
	);
	public static final MessageDefinition MINES_TEMPLATE_ACTIVATE_FAILED = message(
		"mines.template.activate-failed", MessageType.ERROR,
		"The template was captured but could not be activated: {{reason}}"
	);
	public static final MessageDefinition MINES_TEMPLATE_ALREADY_CAPTURING = message(
		"mines.template.already-capturing", MessageType.ERROR, "That mine is already being captured."
	);
	public static final MessageDefinition MINES_TEMPLATE_CAPTURING = message(
		"mines.template.capturing", MessageType.INFO, "Capturing template {{template}} from {{mine}}..."
	);
	public static final MessageDefinition MINES_TEMPLATE_ENABLED = message(
		"mines.template.enabled", MessageType.SUCCESS, "Template reset enabled."
	);
	public static final MessageDefinition MINES_PALETTE_ENABLED = message(
		"mines.palette-enabled", MessageType.SUCCESS, "Palette reset enabled."
	);

	public static final MessageDefinition REGIONS_USAGE = message(
		"regions.usage", MessageType.INFO,
		"Usage: /region <create|redefine|delete|list|info|priority|test|flag|message|owner|member>"
	);
	public static final MessageDefinition REGIONS_SUCCESS = message(
		"regions.success", MessageType.SUCCESS, "{{message}}"
	);
	public static final MessageDefinition REGIONS_OPERATION_FAILED = message(
		"regions.operation-failed", MessageType.ERROR, "Region operation failed: {{reason}}"
	);
	public static final MessageDefinition REGIONS_EMPTY = message(
		"regions.empty", MessageType.INFO, "No custom regions exist in this world."
	);
	public static final MessageDefinition REGIONS_LIST = message(
		"regions.list", MessageType.INFO, "Regions: {{regions}}"
	);
	public static final MessageDefinition REGIONS_UNKNOWN = message(
		"regions.unknown", MessageType.ERROR, "Unknown region in this world."
	);
	public static final MessageDefinition REGIONS_UNKNOWN_PLAYER = message(
		"regions.unknown-player", MessageType.ERROR, "Unknown player. Use an online/cached player name or UUID."
	);
	public static final MessageDefinition REGIONS_ACCESS_LIST = message(
		"regions.access-list", MessageType.INFO, "{{label}}: {{players}}"
	);
	public static final MessageDefinition REGIONS_ENTRY_TEST_UNAVAILABLE = message(
		"regions.entry-test-unavailable", MessageType.ERROR, "Entry is transition-based and cannot be tested at a single position."
	);
	public static final MessageDefinition REGIONS_UNKNOWN_FLAG = message(
		"regions.unknown-flag", MessageType.ERROR, "Unknown region flag."
	);
	public static final MessageDefinition REGIONS_PROTECTION_DISABLED = message(
		"regions.protection-disabled", MessageType.ERROR, "Titan region protection is disabled."
	);
	public static final MessageDefinition REGIONS_TEST_RESULT = message(
		"regions.test-result", MessageType.INFO,
		"Test {{flag}} at {{x}}, {{y}}, {{z}}: {{decision}} ({{reason}})"
	);
	public static final MessageDefinition REGIONS_MATCHING_NONE = message(
		"regions.matching-none", MessageType.INFO, "Matching regions: none"
	);
	public static final MessageDefinition REGIONS_MATCHING = message(
		"regions.matching", MessageType.INFO, "Matching regions: {{regions}}"
	);
	public static final MessageDefinition REGIONS_TRACE_BYPASS = message(
		"regions.trace-bypass", MessageType.INFO, "Trace: protection bypassed by your permission."
	);
	public static final MessageDefinition REGIONS_TRACE_DEFAULT = message(
		"regions.trace-default", MessageType.INFO, "Trace: no region rule decided; world default applied."
	);
	public static final MessageDefinition REGIONS_TRACE_LINE = message(
		"regions.trace-line", MessageType.INFO, "Trace: {{region}}@{{priority}} -> {{decision}} via {{policy}}{{error}}"
	);
	public static final MessageDefinition REGIONS_WINNING_PRIORITY = message(
		"regions.winning-priority", MessageType.INFO, "Winning priority: {{priority}}"
	);

	private MessageDefaults() {
	}

	public static List<MessageDefinition> all() {
		return List.copyOf(ALL);
	}

	private static MessageDefinition message(String key, MessageType type, String defaultText) {
		MessageDefinition definition = MessageDefinition.of(key, type, defaultText);
		ALL.add(definition);
		return definition;
	}

	private static MessageDefinition lines(String key, MessageType type, String... defaultLines) {
		MessageDefinition definition = MessageDefinition.ofLines(key, type, defaultLines);
		ALL.add(definition);
		return definition;
	}
}
