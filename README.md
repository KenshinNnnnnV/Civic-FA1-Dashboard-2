# Civic FA1 Dashboard v1.1.0 — Native Widgets

Target: Honda Civic FA1 2011 / R18A, Android head unit 1280×720 landscape (UIS8581A, 2 GB RAM).

## Architecture

v1.1.0 removes the previous full-screen dashboard-PNG + overlay renderer. `MainActivity` now hosts `DashboardRootView`, which composes independent native Android `View` components for CONNECT, SPORT and DIAGNOSTICS. The only large raster asset retained is the splash image.

Dynamic RPM, speed, sensor values, progress bars, OBD state and diagnostics are rendered by their own components. Disconnected/stale data is blank/`--`; unsupported PIDs are `N/A`. No demo telemetry is generated.

## OBD/ELM327

The retained session-oriented `ObdManager` includes BLE CCCD subscription completion, same-service UART pair validation, generation-based stale-callback rejection, cancelable Classic SPP connect, verified-adapter-only AUTO, a real 41 00 ECU handshake, supported PID discovery, prompt-based commands/timeouts/resync, per-sensor freshness, readiness decoding, multiline Mode 03 parsing and ECU module voltage via PID 0142. ATRV remains a separate adapter-voltage value. Mode 04 is never sent automatically.

## Build

GitHub Actions installs Java 17, Android 34 and Gradle 8.9, runs unit tests, then builds `app-debug.apk`.


## Windows / GitHub Desktop clean update

Do not rely on copy-with-replacement alone when upgrading an older repository: files removed from this package are not deleted by Windows Explorer. After copying the CONTENTS of this package into the repository root, run `PREPARE_REPO_WINDOWS.cmd`. It removes the known legacy DashboardView/full-screen runtime artwork and verifies that the new native root/screens are present. Then review the deletions/additions in GitHub Desktop before Commit/Push.

The Actions workflow invokes the invariant check through `bash`, so Windows executable-bit loss cannot cause a false `Permission denied` failure.
