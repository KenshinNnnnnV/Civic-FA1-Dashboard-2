# Civic FA1 Dashboard v0.7.0

Native Android dashboard for Honda Civic FA1 2011 (R18A 1.8L), optimized for a 1280×720 landscape UIS8581A head unit with 2 GB RAM.

## Product structure

The application now has exactly three modes:

- **CONNECT** — adapter discovery, transport selection, connection state, settings and connection diagnostics.
- **SPORT** — the single primary driving dashboard, with a fixed RPM tachometer + speed and five user-selectable secondary sensor widgets.
- **DIAGNOSTICS** — DTC, readiness, emissions/system state, ECU information and live sensor data only. Connection controls are intentionally not duplicated here.

The former Street mode has been removed.

## UI architecture

The reference images in `design-reference/` are visual specifications only. They are not used as full-screen runtime screenshots.

Runtime artwork in `app/src/main/res/drawable-nodpi/` contains decorative city/car imagery only. All cards, gauges, text, statuses, progress bars, navigation and numeric values are drawn programmatically on a fixed logical 1280×720 canvas.

No dynamic value is baked into a JPEG. Normal mode contains no random/fake telemetry.

## OBD architecture retained from v0.6.0

- Session/generation isolation so stale callbacks cannot overwrite a new connection.
- BLE service discovery and CCCD subscription before ELM initialization.
- BLE UART selection only from supported service/characteristic pairs.
- Cancelable Bluetooth Classic connect with secure → insecure SPP fallback.
- AUTO only reuses a previously ECU-verified adapter.
- ECU connected only after a full valid `41 00 A B C D` response.
- Supported PID bitmap discovery (`0100`, continuation pages such as `0120`, `0140`).
- Sequential ELM command queue; timeout without prompt `>` is incomplete.
- Strict line-oriented parser; text errors are not harvested as hexadecimal payload.
- Per-sensor freshness/stale handling.
- Real readiness parsing from PID `0101`.
- Distinct DTC states: not read, error, no codes, codes present.
- PID `0142` module voltage is separate from ELM `ATRV` adapter voltage.
- PID `0106` and `0107` fuel trims retained.
- Priority polling scheduler; no parallel ELM commands.
- Bounded debug logging and log export.

## Sport widget customization

Tap any of the five secondary Sport cards to open the sensor picker. The tachometer and vehicle speed are fixed and cannot be replaced.

Available secondary sensors currently include coolant, intake, module voltage, adapter voltage, throttle, engine load, fuel level, MAF, MAP, STFT, LTFT, ignition timing and fuel rate.

Selections are stored in `SharedPreferences` and restored after restart.

## Honest data rules

- ECU disconnected / data not available → `--`
- Supported PID but stale → `--`
- Capability bitmap says PID unsupported → `N/A`
- `NO FAULT CODES` only after a valid Mode 03 response with zero stored codes.
- Readiness is never fabricated.
- VIN / calibration / ECU identity stay `N/A` unless genuinely obtained.
- No automatic Mode 04 / DTC clear.

## Build

Expected environment:

- Java 17
- compileSdk / targetSdk 34
- build-tools 34.0.0
- Gradle 8.9

GitHub Actions runs unit tests and `:app:assembleDebug`, then uploads:

`Civic-FA1-Dashboard-v0.7.0-APK`

## Important real-car note

The target adapter is Vgate iCar Pro BLE 4.0 DUAL. The adapter is known to work with the same vehicle through Car Scanner; therefore transport or ECU failures in this application should be debugged as application/protocol issues first rather than blamed on the adapter.
