> Current package: **v0.9.5** — clean runtime UI + performance + dynamic tachometer.

# Civic FA1 Dashboard v0.9.5

Native Android dashboard for Honda Civic FA1 2011 (R18A 1.8L), targeted at the 1280×720 UIS8581A head unit with 2 GB RAM.

## Locked visual design
The approved no-OBD screens in `design-reference/approved-v0.8.1/` remain the visual source of truth for CONNECT / SPORT / DIAGNOSTICS geometry. v0.9.5 keeps the approved three-screen layout and moves the largest dynamic Connect/Diagnostics regions to clean runtime backgrounds so Canvas no longer paints large dark overlay rectangles over the artwork.

The application does not use the numbers in reference art as telemetry. SPORT and DIAGNOSTICS use truthful disconnected references with `--` / `N/A` / `WAITING FOR ECU`; once real data exists, native overlays replace only the changing value regions. CONNECT masks example device/session areas at runtime so fake adapters are never shown as real discoveries.

## Modes
- **CONNECT** — device discovery/selection, BLE/BT/Wi-Fi preference, AUTO policy, settings and staged connection diagnostics.
- **SPORT** — fixed center tachometer/speed plus five configurable secondary sensor widgets.
- **DIAGNOSTICS** — DTC, readiness, live sensors, freeze-frame status, emissions/system status and ECU information.

## Data integrity
- disconnected/missing/stale -> `--`
- unsupported PID -> `N/A`
- no random/simulator telemetry
- `NO FAULT CODES` only after valid Mode 03 zero-code response
- readiness only from real PID 0101
- module voltage PID 0142 remains separate from ELM `ATRV`

## Connection scope
v0.8.1 preserves the v0.7.1 OBD transport/protocol work. The UIS8581A Bluetooth compatibility problem is still diagnosed separately; multiple adapters and Car Scanner on the head unit have failed to establish the data connection, so this visual release does not claim Bluetooth is solved.

## Build
Expected toolchain: Java 17, compileSdk/targetSdk 34, build-tools 34.0.0, Gradle 8.9.

GitHub Actions runs tests and `:app:assembleDebug`, then uploads `Civic-FA1-Dashboard-v0.9.5-APK`.


## Performance and SPORT behavior (v0.9.5)
- OBD telemetry publication to the main thread is capped/coalesced instead of invalidating the full view after every PID.
- SPORT visual animation targets the display frame cadence (~60 FPS) while the RPM arc is converging to the newest real ECU sample; OBD telemetry publication remains capped at ~30 Hz when the adapter can sustain it.
- Fast polling priority is RPM -> throttle -> speed/load, while all ELM commands remain strictly serialized.
- The tachometer has no separate RPM needle/marker. The arc itself fills to current RPM.
- Tach colors are cumulative: green `0-4000`, yellow above `4000`; there is no red active RPM segment and unused range stays dark.
- The large center RPM number is never interpolated or simulated: it shows the newest real ECU value.
- SPORT active-tab glow no longer rises visually above the neighboring tabs.

## App lifecycle (v0.8.3)
Pressing Home or switching to another app minimizes the dashboard normally. The task remains available in Recent Apps and can be resumed without intentionally closing it. The app does not register a BOOT_COMPLETED receiver and does not request overlay permission.
