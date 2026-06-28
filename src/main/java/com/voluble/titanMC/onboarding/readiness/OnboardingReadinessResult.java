package com.voluble.titanMC.onboarding.readiness;

public enum OnboardingReadinessResult {
	READY,
	WAITING_ROOM_FAILED,
	RESOURCE_PACK_UNAVAILABLE,
	RESOURCE_PACK_DECLINED,
	RESOURCE_PACK_FAILED,
	RESOURCE_PACK_TIMEOUT,
	WARMUP_FAILED,
	WARMUP_TIMEOUT;

	public boolean ready() {
		return this == READY;
	}
}
