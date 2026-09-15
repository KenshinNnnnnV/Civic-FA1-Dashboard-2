package com.civicfa1.dashboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SensorFreshnessTest {
    @Test public void disconnectedPidIsNotAvailable() {
        assertEquals(SensorFreshness.State.NOT_AVAILABLE,
                SensorFreshness.classify(false, true, true, false, 88f, 1000, 1100, 1000));
    }

    @Test public void unsupportedPidIsExplicit() {
        assertEquals(SensorFreshness.State.UNSUPPORTED,
                SensorFreshness.classify(true, true, false, false, Float.NaN, 0, 1100, 1000));
    }

    @Test public void staleValueExpiresPerSensor() {
        assertEquals(SensorFreshness.State.STALE,
                SensorFreshness.classify(true, true, true, false, 89f, 1000, 2501, 1500));
    }

    @Test public void freshValueIsValid() {
        assertEquals(SensorFreshness.State.VALID,
                SensorFreshness.classify(true, true, true, false, 89f, 1000, 2400, 1500));
    }

    @Test public void adapterVoltageCanExistBeforeEcuHandshake() {
        assertEquals(SensorFreshness.State.VALID,
                SensorFreshness.classify(false, false, true, true, 12.7f, 1000, 1100, 1000));
    }
}
