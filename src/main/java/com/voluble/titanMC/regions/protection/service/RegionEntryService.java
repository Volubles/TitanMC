package com.voluble.titanMC.regions.protection.service;

import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.index.RegionIndexSnapshot;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionTextFlag;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;
import com.voluble.titanMC.regions.service.RegionEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class RegionEntryService {

	private final RegionEngine regions;
	private final ProtectionBypass bypass;
	private final RegionGroupProvider groups;

	public RegionEntryService(RegionEngine regions, ProtectionBypass bypass) {
		this(regions, bypass, RegionGroupProvider.none());
	}

	public RegionEntryService(
		RegionEngine regions,
		ProtectionBypass bypass,
		RegionGroupProvider groups
	) {
		this.regions = Objects.requireNonNull(regions, "regions");
		this.bypass = Objects.requireNonNull(bypass, "bypass");
		this.groups = Objects.requireNonNull(groups, "groups");
	}

	public Transition evaluate(ProtectionActor actor, BlockPosition from, BlockPosition to) {
		Objects.requireNonNull(actor, "actor");
		MovementMembership movement = movementMembership(from, to, null);
		return evaluate(() -> actor, movement.source(), movement.target());
	}

	public Transition evaluate(
		Supplier<ProtectionActor> actor,
		Membership source,
		Membership target
	) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		List<RegionDefinition> entered = difference(target.regions(), source.regions());
		List<RegionDefinition> exited = difference(source.regions(), target.regions());
		if (entered.isEmpty()) {
			return new Transition(
				ProtectionDecision.ALLOW, Reason.DEFAULT_ALLOW, entered, exited, List.of(),
				Optional.empty(), message(exited, RegionTextFlag.EXIT_MESSAGE), Optional.empty()
			);
		}

		return evaluateEntry(actor, target.position(), entered, exited);
	}

	public Membership membership(BlockPosition position) {
		Objects.requireNonNull(position, "position");
		var snapshot = regions.snapshot();
		return membership(snapshot, position);
	}

	public MovementMembership movementMembership(
		BlockPosition from,
		BlockPosition to,
		Membership cachedSource
	) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		var snapshot = regions.snapshot();
		Membership source = cachedSource != null
			&& cachedSource.version() == snapshot.version()
			&& cachedSource.position().equals(from)
			? cachedSource
			: membership(snapshot, from);
		return new MovementMembership(source, membership(snapshot, to));
	}

	private Transition evaluateEntry(
		Supplier<ProtectionActor> actor,
		BlockPosition target,
		List<RegionDefinition> entered,
		List<RegionDefinition> exited
	) {
		LazyActor lazyActor = new LazyActor(actor);
		Map<GroupKey, Boolean> groupMatches = new HashMap<>();
		List<RegionDecision> decisions = new ArrayList<>();
		try {
			for (RegionDefinition region : entered) {
				boolean requiresActor = region.flags().rules(ProtectionAction.ENTRY).keySet().stream()
					.anyMatch(subject ->
						!subject.equals(com.voluble.titanMC.regions.protection.model.RegionSubject.EVERYONE)
					);
				ProtectionActor evaluatedActor = requiresActor ? lazyActor.get() : null;
				var rule = region.flags().resolve(
					ProtectionAction.ENTRY,
					region.access(),
					evaluatedActor == null ? null : evaluatedActor.playerId(),
					group -> groupMatches.computeIfAbsent(
						new GroupKey(region.worldId(), group),
						ignored -> groups.isInGroup(lazyActor.get(), region.worldId(), group)
					)
				);
				rule.ifPresent(resolved -> decisions.add(
					new RegionDecision(region, resolved.subject(), resolved.decision())
				));
			}
		} catch (RuntimeException exception) {
			return new Transition(
				ProtectionDecision.DENY, Reason.ERROR, entered, exited, List.of(),
				Optional.empty(), Optional.empty(), Optional.of("Region entry rule evaluation failed.")
			);
		}
		for (int start = 0; start < decisions.size();) {
			int priority = decisions.get(start).region().priority();
			int end = start + 1;
			while (end < decisions.size() && decisions.get(end).region().priority() == priority) end++;
			List<RegionDecision> level = decisions.subList(start, end);
			ProtectionDecision decision = level.stream().anyMatch(value -> value.decision() == ProtectionDecision.DENY)
				? ProtectionDecision.DENY
				: ProtectionDecision.ALLOW;
			if (decision == ProtectionDecision.DENY) {
				try {
					ProtectionRequest request = ProtectionRequest.at(
						lazyActor.get(),
						ProtectionAction.ENTRY,
						target
					);
					if (bypass.bypasses(request)) {
						return new Transition(
							ProtectionDecision.ALLOW, Reason.BYPASS, entered, exited, List.of(),
							message(entered, RegionTextFlag.ENTRY_MESSAGE),
							message(exited, RegionTextFlag.EXIT_MESSAGE),
							Optional.empty()
						);
					}
				} catch (RuntimeException exception) {
					return new Transition(
						ProtectionDecision.DENY, Reason.ERROR, entered, exited, List.of(),
						Optional.empty(), Optional.empty(), Optional.of("Region entry bypass check failed.")
					);
				}
			}
			Optional<String> deniedMessage = decision == ProtectionDecision.DENY
				? level.stream()
					.filter(value -> value.decision() == ProtectionDecision.DENY)
					.map(RegionDecision::region)
					.map(region -> region.text().value(RegionTextFlag.ENTRY_DENY_MESSAGE))
					.flatMap(Optional::stream)
					.findFirst()
				: Optional.empty();
			return new Transition(
				decision, Reason.REGION_FLAG, entered, exited, List.copyOf(level),
				decision == ProtectionDecision.ALLOW
					? message(entered, RegionTextFlag.ENTRY_MESSAGE)
					: Optional.empty(),
				decision == ProtectionDecision.ALLOW
					? message(exited, RegionTextFlag.EXIT_MESSAGE)
					: Optional.empty(),
				deniedMessage
			);
		}
		return new Transition(
			ProtectionDecision.ALLOW, Reason.DEFAULT_ALLOW, entered, exited, List.of(),
			message(entered, RegionTextFlag.ENTRY_MESSAGE),
			message(exited, RegionTextFlag.EXIT_MESSAGE),
			Optional.empty()
		);
	}

	private static List<RegionDefinition> difference(
		List<RegionDefinition> candidates,
		List<RegionDefinition> existing
	) {
		if (candidates.isEmpty()) return List.of();
		if (existing.isEmpty()) return candidates;
		List<RegionDefinition> result = null;
		for (RegionDefinition candidate : candidates) {
			boolean found = false;
			for (RegionDefinition current : existing) {
				if (candidate.id().equals(current.id())) {
					found = true;
					break;
				}
			}
			if (!found) {
				if (result == null) result = new ArrayList<>();
				result.add(candidate);
			}
		}
		return result == null ? List.of() : List.copyOf(result);
	}

	private static Membership membership(
		RegionIndexSnapshot snapshot,
		BlockPosition position
	) {
		return new Membership(
			snapshot.version(),
			position,
			snapshot.findAll(position.worldId(), position.x(), position.y(), position.z())
		);
	}

	private static Optional<String> message(List<RegionDefinition> regions, RegionTextFlag flag) {
		return regions.stream()
			.map(region -> region.text().value(flag))
			.flatMap(Optional::stream)
			.findFirst();
	}

	public enum Reason {
		BYPASS,
		REGION_FLAG,
		DEFAULT_ALLOW,
		ERROR
	}

	public record RegionDecision(
		RegionDefinition region,
		com.voluble.titanMC.regions.protection.model.RegionSubject subject,
		ProtectionDecision decision
	) {
		public RegionDecision {
			Objects.requireNonNull(region, "region");
			Objects.requireNonNull(subject, "subject");
			Objects.requireNonNull(decision, "decision");
		}
	}

	private static final class LazyActor {
		private final Supplier<ProtectionActor> supplier;
		private ProtectionActor value;

		private LazyActor(Supplier<ProtectionActor> supplier) {
			this.supplier = Objects.requireNonNull(supplier, "supplier");
		}

		private ProtectionActor get() {
			if (value == null) {
				value = Objects.requireNonNull(supplier.get(), "actor supplier result");
			}
			return value;
		}
	}

	private record GroupKey(com.voluble.titanMC.regions.model.WorldId worldId, String group) {}

	public record Membership(
		long version,
		BlockPosition position,
		List<RegionDefinition> regions
	) {
		public Membership {
			if (version < 0L) throw new IllegalArgumentException("version must not be negative");
			Objects.requireNonNull(position, "position");
			regions = List.copyOf(regions);
		}
	}

	public record MovementMembership(Membership source, Membership target) {
		public MovementMembership {
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(target, "target");
			if (source.version() != target.version()) {
				throw new IllegalArgumentException("movement memberships must use the same snapshot");
			}
		}
	}

	public record Transition(
		ProtectionDecision decision,
		Reason reason,
		List<RegionDefinition> entered,
		List<RegionDefinition> exited,
		List<RegionDecision> decidingRegions,
		Optional<String> entryMessage,
		Optional<String> exitMessage,
		Optional<String> denyMessage
	) {
		public Transition {
			Objects.requireNonNull(decision, "decision");
			if (!decision.explicit()) throw new IllegalArgumentException("entry decision must be explicit");
			Objects.requireNonNull(reason, "reason");
			entered = List.copyOf(entered);
			exited = List.copyOf(exited);
			decidingRegions = List.copyOf(decidingRegions);
			entryMessage = Objects.requireNonNull(entryMessage, "entryMessage");
			exitMessage = Objects.requireNonNull(exitMessage, "exitMessage");
			denyMessage = Objects.requireNonNull(denyMessage, "denyMessage");
		}

		public boolean allowed() {
			return decision == ProtectionDecision.ALLOW;
		}
	}
}
