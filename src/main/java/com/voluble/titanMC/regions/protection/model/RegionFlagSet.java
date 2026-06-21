package com.voluble.titanMC.regions.protection.model;

import com.voluble.titanMC.regions.model.RegionAccessSet;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class RegionFlagSet {

	private static final RegionFlagSet EMPTY = new RegionFlagSet(Map.of());

	private final Map<ProtectionAction, Map<RegionSubject, ProtectionDecision>> decisions;

	private RegionFlagSet(Map<ProtectionAction, Map<RegionSubject, ProtectionDecision>> decisions) {
		EnumMap<ProtectionAction, Map<RegionSubject, ProtectionDecision>> copy =
			new EnumMap<>(ProtectionAction.class);
		decisions.forEach((action, rules) -> copy.put(action, Map.copyOf(rules)));
		this.decisions = Map.copyOf(copy);
	}

	public static RegionFlagSet empty() {
		return EMPTY;
	}

	public static RegionFlagSet of(Map<ProtectionAction, ProtectionDecision> decisions) {
		Objects.requireNonNull(decisions, "decisions");
		EnumMap<ProtectionAction, ProtectionDecision> explicit = new EnumMap<>(ProtectionAction.class);
		decisions.forEach((action, decision) -> {
			Objects.requireNonNull(action, "flag action");
			Objects.requireNonNull(decision, "flag decision");
			if (decision.explicit()) explicit.put(action, decision);
		});
		if (explicit.isEmpty()) return EMPTY;
		EnumMap<ProtectionAction, Map<RegionSubject, ProtectionDecision>> scoped =
			new EnumMap<>(ProtectionAction.class);
		explicit.forEach((action, decision) -> scoped.put(action, Map.of(RegionSubject.EVERYONE, decision)));
		return new RegionFlagSet(scoped);
	}

	public static RegionFlagSet ofScoped(
		Map<ProtectionAction, ? extends Map<RegionSubject, ProtectionDecision>> decisions
	) {
		Objects.requireNonNull(decisions, "decisions");
		EnumMap<ProtectionAction, Map<RegionSubject, ProtectionDecision>> explicit =
			new EnumMap<>(ProtectionAction.class);
		decisions.forEach((action, rules) -> {
			Objects.requireNonNull(action, "flag action");
			Objects.requireNonNull(rules, "flag rules");
			Map<RegionSubject, ProtectionDecision> checked = new LinkedHashMap<>();
			rules.forEach((subject, decision) -> {
				Objects.requireNonNull(subject, "flag subject");
				Objects.requireNonNull(decision, "flag decision");
				if (decision.explicit()) checked.put(subject, decision);
			});
			if (!checked.isEmpty()) explicit.put(action, Map.copyOf(checked));
		});
		return explicit.isEmpty() ? EMPTY : new RegionFlagSet(explicit);
	}

	public ProtectionDecision decision(ProtectionAction action) {
		return decision(action, RegionSubject.EVERYONE);
	}

	public ProtectionDecision decision(ProtectionAction action, RegionSubject subject) {
		Map<RegionSubject, ProtectionDecision> rules =
			decisions.get(Objects.requireNonNull(action, "action"));
		return rules == null
			? ProtectionDecision.ABSTAIN
			: rules.getOrDefault(Objects.requireNonNull(subject, "subject"), ProtectionDecision.ABSTAIN);
	}

	public Map<ProtectionAction, ProtectionDecision> explicitDecisions() {
		EnumMap<ProtectionAction, ProtectionDecision> everyone = new EnumMap<>(ProtectionAction.class);
		decisions.forEach((action, rules) -> {
			ProtectionDecision decision = rules.get(RegionSubject.EVERYONE);
			if (decision != null) everyone.put(action, decision);
		});
		return Map.copyOf(everyone);
	}

	public Map<ProtectionAction, Map<RegionSubject, ProtectionDecision>> explicitRules() {
		return decisions;
	}

	public Map<RegionSubject, ProtectionDecision> rules(ProtectionAction action) {
		return decisions.getOrDefault(Objects.requireNonNull(action, "action"), Map.of());
	}

	public boolean hasGroupRules(ProtectionAction action) {
		return rules(action).keySet().stream().anyMatch(subject -> subject.type() == RegionSubject.Type.GROUP);
	}

	public RegionFlagSet with(ProtectionAction action, ProtectionDecision decision) {
		return with(action, RegionSubject.EVERYONE, decision);
	}

	public RegionFlagSet with(
		ProtectionAction action,
		RegionSubject subject,
		ProtectionDecision decision
	) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(subject, "subject");
		Objects.requireNonNull(decision, "decision");
		EnumMap<ProtectionAction, Map<RegionSubject, ProtectionDecision>> updated =
			new EnumMap<>(ProtectionAction.class);
		updated.putAll(decisions);
		Map<RegionSubject, ProtectionDecision> rules =
			new LinkedHashMap<>(updated.getOrDefault(action, Map.of()));
		if (decision.explicit()) rules.put(subject, decision);
		else rules.remove(subject);
		if (rules.isEmpty()) updated.remove(action);
		else updated.put(action, Map.copyOf(rules));
		return ofScoped(updated);
	}

	public Optional<ResolvedRule> resolve(
		ProtectionAction action,
		RegionAccessSet access,
		UUID playerId,
		Predicate<String> groupMembership
	) {
		Objects.requireNonNull(access, "access");
		Objects.requireNonNull(groupMembership, "groupMembership");
		Map<RegionSubject, ProtectionDecision> rules = rules(action);
		if (rules.isEmpty()) return Optional.empty();
		int winningSpecificity = Integer.MIN_VALUE;
		RegionSubject winningSubject = null;
		ProtectionDecision winningDecision = ProtectionDecision.ABSTAIN;
		for (Map.Entry<RegionSubject, ProtectionDecision> entry : rules.entrySet()) {
			RegionSubject subject = entry.getKey();
			if (!matches(subject, access, playerId, groupMembership)) continue;
			int specificity = subject.specificity();
			if (specificity > winningSpecificity
				|| specificity == winningSpecificity && entry.getValue() == ProtectionDecision.DENY) {
				winningSpecificity = specificity;
				winningSubject = subject;
				winningDecision = entry.getValue();
			}
		}
		return winningSubject == null
			? Optional.empty()
			: Optional.of(new ResolvedRule(winningSubject, winningDecision));
	}

	private static boolean matches(
		RegionSubject subject,
		RegionAccessSet access,
		UUID playerId,
		Predicate<String> groupMembership
	) {
		return switch (subject.type()) {
			case EVERYONE -> true;
			case OWNERS -> access.isOwner(playerId);
			case MEMBERS -> access.isMember(playerId);
			case NONOWNERS -> playerId != null && !access.isOwner(playerId);
			case NONMEMBERS -> playerId != null && !access.isMember(playerId);
			case GROUP -> playerId != null && groupMembership.test(subject.value());
		};
	}

	public record ResolvedRule(RegionSubject subject, ProtectionDecision decision) {
		public ResolvedRule {
			Objects.requireNonNull(subject, "subject");
			Objects.requireNonNull(decision, "decision");
			if (!decision.explicit()) throw new IllegalArgumentException("resolved decision must be explicit");
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RegionFlagSet flags && decisions.equals(flags.decisions);
	}

	@Override
	public int hashCode() {
		return decisions.hashCode();
	}

	@Override
	public String toString() {
		return decisions.toString();
	}
}
