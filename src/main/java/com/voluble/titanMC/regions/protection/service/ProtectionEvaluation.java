package com.voluble.titanMC.regions.protection.service;

import com.voluble.titanMC.regions.model.BlockPosition;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.model.ProtectionResolution;
import com.voluble.titanMC.regions.protection.model.TransitionResolution;
import com.voluble.titanMC.regions.protection.model.TransitionRule;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.ProtectionDefaults;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyEvaluationRegistry;
import com.voluble.titanMC.regions.protection.policy.RegionGroupProvider;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ProtectionEvaluation {

	final ProtectionService service;
	final RegionLookup regions;
	final RegionPolicyEvaluationRegistry policies;
	final ProtectionDefaults defaults;
	final ProtectionBypass bypass;
	final RegionGroupMembership groups;
	final ProtectionResolution.Reason initializationReason;
	final String initializationError;
	private final ProtectionActor actor;
	private final Instant evaluatedAt;

	ProtectionEvaluation(
		ProtectionService service,
		RegionLookup regions,
		RegionPolicyEvaluationRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass,
		RegionGroupProvider groupProvider,
		ProtectionActor actor,
		Instant evaluatedAt
	) {
		this.service = Objects.requireNonNull(service, "service");
		this.regions = Objects.requireNonNull(regions, "regions");
		this.policies = Objects.requireNonNull(policies, "policies");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		this.bypass = Objects.requireNonNull(bypass, "bypass");
		this.groups = new RegionGroupMembership(groupProvider, actor);
		this.actor = Objects.requireNonNull(actor, "actor");
		this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		this.initializationReason = null;
		this.initializationError = null;
	}

	ProtectionEvaluation(
		ProtectionService service,
		RegionLookup regions,
		RegionPolicyEvaluationRegistry policies,
		ProtectionDefaults defaults,
		ProtectionBypass bypass,
		RegionGroupProvider groupProvider,
		ProtectionActor actor,
		Instant evaluatedAt,
		ProtectionResolution.Reason initializationReason,
		String initializationError
	) {
		this.service = Objects.requireNonNull(service, "service");
		this.regions = Objects.requireNonNull(regions, "regions");
		this.policies = Objects.requireNonNull(policies, "policies");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		this.bypass = Objects.requireNonNull(bypass, "bypass");
		this.groups = new RegionGroupMembership(groupProvider, actor);
		this.actor = Objects.requireNonNull(actor, "actor");
		this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		this.initializationReason = Objects.requireNonNull(initializationReason, "initializationReason");
		this.initializationError = Objects.requireNonNull(initializationError, "initializationError");
	}

	public ProtectionActor actor() {
		return actor;
	}

	public Instant evaluatedAt() {
		return evaluatedAt;
	}

	public long regionSnapshotVersion() {
		return regions.version();
	}

	public ProtectionResolution resolve(ProtectionRequest request) {
		if (!actor.equals(request.actor())) throw new IllegalArgumentException("request actor differs from evaluation actor");
		return service.resolve(this, request);
	}

	public TransitionResolution resolveTransition(ProtectionRequest request, TransitionRule rule) {
		Objects.requireNonNull(rule, "rule");
		if (request.source().isEmpty() && rule != TransitionRule.TARGET) {
			throw new IllegalArgumentException("transition rule requires a source position");
		}
		ProtectionResolution sourceResolution = null;
		ProtectionResolution targetResolution = null;
		if (rule != TransitionRule.TARGET) {
			BlockPosition source = request.source().orElseThrow();
			sourceResolution = resolve(ProtectionRequest.at(actor, request.action(), source));
		}
		if (rule != TransitionRule.SOURCE) {
			targetResolution = resolve(ProtectionRequest.at(actor, request.action(), request.target()));
		}
		boolean sourceAllowed = sourceResolution != null && sourceResolution.allowed();
		boolean targetAllowed = targetResolution != null && targetResolution.allowed();
		boolean allowed = switch (rule) {
			case SOURCE -> sourceAllowed;
			case TARGET -> targetAllowed;
			case BOTH -> sourceAllowed && targetAllowed;
			case EITHER -> sourceAllowed || targetAllowed;
		};
		return new TransitionResolution(
			rule,
			allowed ? ProtectionDecision.ALLOW : ProtectionDecision.DENY,
			Optional.ofNullable(sourceResolution),
			Optional.ofNullable(targetResolution)
		);
	}
}
