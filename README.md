# Civic FA1 Dashboard v1.0.2

Android automotive dashboard for Honda Civic FA1 2011 / R18A 1.8L, targeted at a 1280×720 Android head unit (UIS8581A class hardware).

## Modes
- **CONNECT** — OBD adapter/device selection, transport settings, connection status and diagnostics.
- **SPORT** — live RPM/speed and configurable sensor widgets.
- **DIAGNOSTICS** — DTC/readiness/live sensor/system information.

## v1.0 UI lock
The v1 shell uses one fixed geometry across all three screens:
- same top-header dimensions and angles;
- same bottom-navigation dimensions and angles;
- no clock and no Wi-Fi icon in the header;
- CONNECT accent = blue/cyan;
- SPORT accent = red;
- DIAGNOSTICS accent = green;
- inactive bottom tabs stay neutral/dark;
- only the selected tab receives its mode color/glow.

Final references are in `design-reference/final-v1/`.

## SPORT tachometer
The tachometer arc is the RPM indicator. The unused portion remains dark and the active portion fills cumulatively through green, yellow and red zones as RPM increases. The large RPM number remains the latest truthful ECU value; visual smoothing applies to the arc only.

## OBD integrity
- missing/stale data -> `--`;
- unsupported PID -> `N/A`;
- no random telemetry;
- Mode 03 / readiness state remains separated from display styling;
- module voltage PID 0142 remains separate from adapter `ATRV`.

## Build on GitHub
1. Put the project in a GitHub repository root.
2. Commit and push to `main`.
3. Open **Actions -> Build Android APK**.
4. Download artifact **Civic-FA1-Dashboard-v1.0.2-APK**.

Toolchain in workflow: Java 17, Android API 34, Build Tools 34.0.0, Gradle 8.9.


## v1.0.2 Sport behavior
- RPM is shown graphically by the dynamic green/yellow/red arc only; there is no tachometer needle.
- Five Sport sensor widgets can be changed by tapping them; selections persist between launches.
