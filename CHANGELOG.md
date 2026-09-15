# Changelog

## v0.7.0

### Product/UI rewrite
- Removed Street mode. Main navigation is now CONNECT / SPORT / DIAGNOSTICS.
- Rebuilt the UI on a fixed 1280×720 logical canvas.
- Replaced the failed v0.6 visual layout with a reference-driven programmatic widget layout.
- Runtime backgrounds now contain decorative artwork only; no live values or status cards are baked into images.
- Connect mode now fills the working area with nine functional cards: adapter, ECU link, connection type, actions, settings, connection status, recent devices, transport options and connection diagnostics.
- Removed Live Data Preview from Connect mode as requested.
- Sport mode is now the single driving dashboard with a large RPM tachometer, speed and five configurable sensor cards.
- Added tap-to-select Sport sensor widgets persisted in SharedPreferences.
- Diagnostics no longer duplicates OBD/ECU connection controls.
- Added diagnostic top metrics, stored-DTC panel, readiness monitor panel, live sensor list, freeze-frame status, emissions/system status and ECU information.
- Bottom tabs share one fixed geometry; active state changes accent only.

### Data honesty
- Added explicit UI states VALID / STALE / UNSUPPORTED / NOT_AVAILABLE through `SensorFreshness`.
- Unsupported PID displays `N/A`; missing/stale data displays `--`.
- Header uses real OBD state; no production SIMULATOR label.
- No random telemetry in normal mode.

### OBD / connection
- Preserved v0.6 session isolation, BLE CCCD sequencing, strict ECU handshake, capability discovery, parser, freshness, DTC/readiness states and priority polling.
- Connection timeout is now configurable (3000–15000 ms) and used by Wi-Fi and Bluetooth Classic connection timeout handling.
- Auto-detect protocol preference controls whether `ATSP0` is explicitly sent during ELM initialization.
- Low Power Mode slows polling/background diagnostic refresh without parallelizing commands.
- Added state-transition entries to the bounded debug log.
- Debug log export now writes to app external-files storage when available.

### Tests
- Added STOPPED and UNABLE TO CONNECT parser tests.
- Added second supported-PID page test.
- Added multiple-DTC Mode 03 test.
- Added pure-Java sensor freshness tests including stale, unsupported and adapter-voltage cases.

### Build
- Version code 17 / version name 0.7.0.
- GitHub Actions artifact renamed to `Civic-FA1-Dashboard-v0.7.0-APK`.
