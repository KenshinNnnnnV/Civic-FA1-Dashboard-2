# Civic FA1 Dashboard v0.7.1

Native Android dashboard for Honda Civic FA1 2011 (R18A 1.8L), designed for a 1280×720 UIS8581A head unit with 2 GB RAM.

## Modes
- **CONNECT** — adapter/device/transport selection, settings, connection state and connection diagnostics.
- **SPORT** — primary driving dashboard with RPM, speed and five configurable sensor widgets.
- **DIAGNOSTICS** — DTC, readiness, emissions/system status, ECU information and live sensor data.

## Data rules
- No random/fake telemetry in normal mode.
- Disconnected/missing/stale data -> `--`.
- Unsupported PID -> `N/A`.
- `NO FAULT CODES` only after valid Mode 03.
- Readiness only from actual PID 0101.
- PID 0142 module voltage is distinct from ELM `ATRV` adapter voltage.

## Vgate iCar Pro support
v0.7.1 adds the 18F0/2AF0/2AF1 BLE UART profile used by Vgate iCar Pro / VLink BLE hardware, while retaining explicit FFF0, FFE0 and Nordic UART profiles. Bluetooth Classic SPP remains available as a fallback for dual-mode Vgate hardware.

## Build
Expected toolchain:
- Java 17
- compileSdk / targetSdk 34
- build-tools 34.0.0
- Gradle 8.9

GitHub Actions runs unit tests and `:app:assembleDebug`, then uploads:

`Civic-FA1-Dashboard-v0.7.1-APK`

## Design
Use `DESIGN_SPEC.md` and the normalized files in `design-reference/` as the visual source of truth. Runtime backgrounds are decorative-only.
