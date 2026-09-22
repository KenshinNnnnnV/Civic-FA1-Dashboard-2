package com.civicfa1.dashboard;

import java.util.Locale;

enum SensorType {
    RPM("ENGINE RPM", "rpm", 0, 8000, 0x0C),
    SPEED("VEHICLE SPEED", "km/h", 0, 240, 0x0D),
    COOLANT("COOLANT TEMP", "°C", 50, 130, 0x05),
    INTAKE("INTAKE TEMP", "°C", -20, 60, 0x0F),
    THROTTLE("THROTTLE", "%", 0, 100, 0x11),
    LOAD("ENGINE LOAD", "%", 0, 100, 0x04),
    MODULE_VOLTAGE("VOLTAGE", "V", 10, 16, 0x42),
    ADAPTER_VOLTAGE("ADAPTER VOLTAGE", "V", 10, 16, -1),
    MAF("MAF AIR FLOW", "g/s", 0, 50, 0x10),
    MAP("MAP PRESSURE", "kPa", 0, 255, 0x0B),
    SHORT_FUEL_TRIM("FUEL TRIM STFT", "%", -25, 25, 0x06),
    LONG_FUEL_TRIM("FUEL TRIM LTFT", "%", -25, 25, 0x07),
    FUEL_LEVEL("FUEL LEVEL", "%", 0, 100, 0x2F),
    TIMING("TIMING ADVANCE", "°", -20, 60, 0x0E),
    FUEL_RATE("FUEL RATE", "L/h", 0, 80, 0x5E);

    final String label, unit;
    final float min, max;
    final int pid;
    SensorType(String label, String unit, float min, float max, int pid) {
        this.label=label; this.unit=unit; this.min=min; this.max=max; this.pid=pid;
    }

    float value(ObdManager.Telemetry t) {
        switch (this) {
            case RPM: return t.rpm; case SPEED: return t.speed; case COOLANT: return t.coolant;
            case INTAKE: return t.intake; case THROTTLE: return t.throttle; case LOAD: return t.load;
            case MODULE_VOLTAGE: return t.voltage; case ADAPTER_VOLTAGE: return t.adapterVoltage;
            case MAF: return t.maf; case MAP: return t.map; case SHORT_FUEL_TRIM: return t.shortFuelTrim;
            case LONG_FUEL_TRIM: return t.longFuelTrim; case FUEL_LEVEL: return t.fuel;
            case TIMING: return t.timing; case FUEL_RATE: return t.fuelRate;
            default: return Float.NaN;
        }
    }

    long at(ObdManager.Telemetry t) {
        switch (this) {
            case RPM: return t.rpmAt; case SPEED: return t.speedAt; case COOLANT: return t.coolantAt;
            case INTAKE: return t.intakeAt; case THROTTLE: return t.throttleAt; case LOAD: return t.loadAt;
            case MODULE_VOLTAGE: return t.voltageAt; case ADAPTER_VOLTAGE: return t.adapterVoltageAt;
            case MAF: return t.mafAt; case MAP: return t.mapAt; case SHORT_FUEL_TRIM: return t.shortFuelTrimAt;
            case LONG_FUEL_TRIM: return t.longFuelTrimAt; case FUEL_LEVEL: return t.fuelAt;
            case TIMING: return t.timingAt; case FUEL_RATE: return t.fuelRateAt;
            default: return 0L;
        }
    }

    long ttlMs() {
        switch (this) {
            case RPM: return 1600; case SPEED: case THROTTLE: case LOAD: return 1800;
            case MAF: case MAP: case TIMING: return 3000;
            case SHORT_FUEL_TRIM: return 4000; case LONG_FUEL_TRIM: return 6000;
            case FUEL_LEVEL: return 15000; default: return 7000;
        }
    }

    boolean supported(ObdManager.Telemetry t) {
        return this == ADAPTER_VOLTAGE || !t.capabilitiesKnown || pid < 0 || t.supports(pid);
    }

    String format(float v) {
        if (Float.isNaN(v)) return "--";
        switch (this) {
            case MODULE_VOLTAGE: case ADAPTER_VOLTAGE: case MAF: case SHORT_FUEL_TRIM:
            case LONG_FUEL_TRIM: case FUEL_RATE: return String.format(Locale.US, "%.1f", v);
            default: return String.format(Locale.US, "%.0f", v);
        }
    }
}
