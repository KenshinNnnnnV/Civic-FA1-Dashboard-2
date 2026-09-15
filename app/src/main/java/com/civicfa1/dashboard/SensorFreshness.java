package com.civicfa1.dashboard;

/** Pure-Java sensor state classification shared by UI and unit tests. */
public final class SensorFreshness {
    private SensorFreshness() { }

    public enum State { VALID, STALE, UNSUPPORTED, NOT_AVAILABLE }

    public static State classify(boolean ecuConnected,
                                 boolean capabilitiesKnown,
                                 boolean supported,
                                 boolean capabilityIndependent,
                                 float value,
                                 long timestampMs,
                                 long nowMs,
                                 long ttlMs) {
        if (!ecuConnected && !capabilityIndependent) return State.NOT_AVAILABLE;
        if (!capabilityIndependent && capabilitiesKnown && !supported) return State.UNSUPPORTED;
        if (Float.isNaN(value) || timestampMs <= 0L) return ecuConnected || capabilityIndependent ? State.STALE : State.NOT_AVAILABLE;
        if (nowMs - timestampMs > ttlMs) return State.STALE;
        return State.VALID;
    }
}
