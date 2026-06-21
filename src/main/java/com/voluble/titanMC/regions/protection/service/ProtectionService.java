package com.voluble.titanMC.regions.protection.service;

import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.model.ProtectionResolution;
import com.voluble.titanMC.regions.protection.model.RegionPolicyEvaluation;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.ProtectionDefaults;
import com.voluble.titanMC.regions.protection.policy.ProtectionEvaluationContext;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyEvaluationRegistry;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;
import com.voluble.titanMC.regions.service.RegionEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class ProtectionService {

	private static final Comparator<RegionDefinition> REGION_ORDER = Comparator
		.comparingInt(RegionDefinition::priority).reversed()
		.thenComparing(RegionDefinition::key)
		.thenComparing(RegionDefinition::id);

	private final Supplier<RegionLookup> regionSource;
	private final RegionPolicyRegistry policies;
	private final ProtectionDefaults defaults;
	private final ProtectionBypass bypass;
	private final RegionGroupProvider groups;

	public ProtectionService(
		RegionLookup regions,
		RegionPolicyRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass
	) {
		this(regions, policies, defaults, bypass, RegionGroupProvider.none());
	}

	public ProtectionService(
		RegionLookup regions,
		RegionPolicyRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass,
		RegionGroupProvider groups
	) {
		this(() -> regions, policies, defaults, bypass, groups);
		Objects.requireNonNull(regions, "regions");
	}

	private ProtectionService(
		Supplier<RegionLookup> regionSource,
		RegionPolicyRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass,
		RegionGroupProvider groups
	) {
		this.regionSource = Objects.requireNonNull(regionSource, "regionSource");
		this.policies = Objects.requireNonNull(policies, "policies");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		this.bypass = Objects.requireNonNull(bypass, "bypass");
		this.groups = Objects.requireNonNull(groups, "groups");
	}

	public static ProtectionService forEngine(
		RegionEngine engine,
		RegionPolicyRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass
	) {
		return forEngine(engine, policies, defaults, bypass, RegionGroupProvider.none());
	}

	public static ProtectionService forEngine(
		RegionEngine engine,
		RegionPolicyRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass,
		RegionGroupProvider groups
	) {
		Objects.requireNonNull(engine, "engine");
		return new ProtectionService(
			() -> RegionLookup.from(engine.readView()), policies, defaults, bypass, groups
		);
	}

	public ProtectionEvaluation beginEvaluation(ProtectionActor actor, Instant evaluatedAt) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		ProtectionEvaluationContext context = new ProtectionEvaluationContext(actor, evaluatedAt);
		RegionLookup regions;
		try {
			regions = Objects.requireNonNull(regionSource.get(), "region source returned null");
		} catch (RuntimeException exception) {
			return failedEvaluation(actor, evaluatedAt, ProtectionResolution.Reason.LOOKUP_ERROR, exception);
		}
		RegionPolicyEvaluationRegistry evaluationPolicies;
		try {
			evaluationPolicies = policies.openEvaluation(context);
		} catch (RuntimeException exception) {
			return failedEvaluation(regions, actor, evaluatedAt, ProtectionResolution.Reason.POLICY_ERROR, exception);
		}
		ProtectionDefaults evaluationDefaults;
		try {
			evaluationDefaults = Objects.requireNonNull(defaults.openEvaluation(context), "defaults returned null evaluation");
		} catch (RuntimeException exception) {
			return failedEvaluation(regions, actor, evaluatedAt, ProtectionResolution.Reason.DEFAULT_ERROR, exception);
		}
		ProtectionBypass evaluationBypass;
		try {
			evaluationBypass = Objects.requireNonNull(bypass.openEvaluation(context), "bypass returned null evaluation");
		} catch (RuntimeException exception) {
			return failedEvaluation(regions, actor, evaluatedAt, ProtectionResolution.Reason.BYPASS_ERROR, exception);
		}
		return new ProtectionEvaluation(
			this, regions, evaluationPolicies, evaluationDefaults, evaluationBypass, groups, actor, evaluatedAt
		);
	}

	public ProtectionResolution resolve(ProtectionRequest request) {
		Objects.requireNonNull(request, "request");
		return beginEvaluation(request.actor(), Instant.now()).resolve(request);
	}

	ProtectionResolution resolve(ProtectionEvaluation evaluation, ProtectionRequest request) {
		Objects.requireNonNull(evaluation, "evaluation");
		Objects.requireNonNull(request, "request");
		if (evaluation.initializationReason != null) {
			return resolution(
				ProtectionDecision.DENY,
				evaluation.initializationReason,
				OptionalInt.empty(),
				List.of(),
				evaluation.initializationError
			);
		}
		try {
			if (evaluation.bypass.bypasses(request)) {
				return resolution(
					ProtectionDecision.ALLOW, ProtectionResolution.Reason.BYPASS,
					OptionalInt.empty(), List.of(), "Actor bypassed protection"
				);
			}
		} catch (RuntimeException exception) {
			return resolution(
				ProtectionDecision.DENY, ProtectionResolution.Reason.BYPASS_ERROR,
				OptionalInt.empty(), List.of(), safeError(exception)
			);
		}

		BlockPosition target = request.target();
		List<RegionDefinition> applicable;
		try {
			List<RegionDefinition> found = Objects.requireNonNull(
				evaluation.regions.findAll(target.worldId(), target.x(), target.y(), target.z()),
				"region lookup returned null"
			);
			if (found.stream().anyMatch(Objects::isNull)) {
				throw new NullPointerException("region lookup contained null");
			}
			applicable = new ArrayList<>(found);
		} catch (RuntimeException exception) {
			return resolution(
				ProtectionDecision.DENY, ProtectionResolution.Reason.LOOKUP_ERROR,
				OptionalInt.empty(), List.of(), safeError(exception)
			);
		}
		applicable.sort(REGION_ORDER);

		List<RegionPolicyEvaluation> trace = new ArrayList<>();
		for (int start = 0; start < applicable.size();) {
			int priority = applicable.get(start).priority();
			int end = start + 1;
			while (end < applicable.size() && applicable.get(end).priority() == priority) end++;
			ProtectionDecision levelDecision = ProtectionDecision.ABSTAIN;
			for (int index = start; index < end; index++) {
				RegionDefinition region = applicable.get(index);
				java.util.Optional<com.voluble.titanMC.regions.protection.model.RegionFlagSet.ResolvedRule> flagRule;
				try {
					flagRule = region.flags().resolve(
						request.action(),
						region.access(),
						request.actor().playerId(),
						group -> evaluation.groups.matches(region.worldId(), group)
					);
				} catch (RuntimeException exception) {
					trace.add(RegionPolicyEvaluation.failed(
						region.id(), region.key(), priority, "region-flags", safeError(exception)
					));
					return resolution(
						ProtectionDecision.DENY, ProtectionResolution.Reason.POLICY_ERROR,
						OptionalInt.of(priority), trace, safeError(exception)
					);
				}
				if (flagRule.isPresent()) {
					var resolvedFlag = flagRule.orElseThrow();
					ProtectionDecision flagDecision = resolvedFlag.decision();
					trace.add(RegionPolicyEvaluation.decided(
						region.id(), region.key(), priority,
						resolvedFlag.subject().equals(
							com.voluble.titanMC.regions.protection.model.RegionSubject.EVERYONE
						)
							? "region-flags"
							: "region-flags:" + resolvedFlag.subject().externalName(),
						flagDecision
					));
					if (flagDecision == ProtectionDecision.DENY) levelDecision = ProtectionDecision.DENY;
					else if (levelDecision == ProtectionDecision.ABSTAIN) levelDecision = ProtectionDecision.ALLOW;
					continue;
				}
				RegionPolicyEvaluationRegistry.Entry entry = evaluation.policies.find(region.key().namespace());
				if (entry == null) {
					trace.add(RegionPolicyEvaluation.decided(
						region.id(), region.key(), priority, "unregistered", ProtectionDecision.ABSTAIN
					));
					continue;
				}
				ProtectionDecision decision;
				try {
					decision = Objects.requireNonNull(
						entry.evaluator().decide(request, region), "policy returned null"
					);
					trace.add(RegionPolicyEvaluation.decided(
						region.id(), region.key(), priority, entry.policyId(), decision
					));
				} catch (RuntimeException exception) {
					trace.add(RegionPolicyEvaluation.failed(
						region.id(), region.key(), priority, entry.policyId(), safeError(exception)
					));
					return resolution(
						ProtectionDecision.DENY, ProtectionResolution.Reason.POLICY_ERROR,
						OptionalInt.of(priority), trace, safeError(exception)
					);
				}
				if (decision == ProtectionDecision.DENY) levelDecision = ProtectionDecision.DENY;
				else if (decision == ProtectionDecision.ALLOW && levelDecision == ProtectionDecision.ABSTAIN) {
					levelDecision = ProtectionDecision.ALLOW;
				}
			}
			if (levelDecision.explicit()) {
				return resolution(
					levelDecision, ProtectionResolution.Reason.REGION_POLICY,
					OptionalInt.of(priority), trace, "Explicit region policy decision"
				);
			}
			start = end;
		}

		try {
			ProtectionDecision decision = Objects.requireNonNull(
				evaluation.defaults.decide(request), "defaults returned null"
			);
			if (!decision.explicit()) throw new IllegalStateException("defaults returned ABSTAIN");
			return resolution(
				decision, ProtectionResolution.Reason.WORLD_DEFAULT,
				OptionalInt.empty(), trace, "World default decided"
			);
		} catch (RuntimeException exception) {
			return resolution(
				ProtectionDecision.DENY, ProtectionResolution.Reason.DEFAULT_ERROR,
				OptionalInt.empty(), trace, safeError(exception)
			);
		}
	}

	public boolean allowed(ProtectionRequest request) {
		return resolve(request).allowed();
	}

	private ProtectionEvaluation failedEvaluation(
		ProtectionActor actor,
		Instant evaluatedAt,
		ProtectionResolution.Reason reason,
		RuntimeException exception
	) {
		RegionLookup failedLookup = (worldId, x, y, z) -> {
			throw new IllegalStateException("region evaluation could not be opened", exception);
		};
		return failedEvaluation(failedLookup, actor, evaluatedAt, reason, exception);
	}

	private ProtectionEvaluation failedEvaluation(
		RegionLookup regions,
		ProtectionActor actor,
		Instant evaluatedAt,
		ProtectionResolution.Reason reason,
		RuntimeException exception
	) {
		ProtectionEvaluationContext context = new ProtectionEvaluationContext(actor, evaluatedAt);
		RegionPolicyEvaluationRegistry empty = RegionPolicyRegistry.builder().build().openEvaluation(context);
		return new ProtectionEvaluation(
			this, regions, empty, request -> ProtectionDecision.DENY, ProtectionBypass.none(),
			groups, actor, evaluatedAt, reason, safeError(exception)
		);
	}

	private static ProtectionResolution resolution(
		ProtectionDecision decision,
		ProtectionResolution.Reason reason,
		OptionalInt priority,
		List<RegionPolicyEvaluation> evaluations,
		String explanation
	) {
		return new ProtectionResolution(decision, reason, priority, evaluations, explanation);
	}

	private static String safeError(RuntimeException exception) {
		String message = exception.getMessage();
		return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
	}
}
