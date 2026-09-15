# Civic FA1 Dashboard v0.6.0 — road test checklist

Target: Honda Civic FA1 2011, R18A 1.8, UIS8581A 1280×720 head unit, Vgate iCar Pro BLE 4.0 DUAL.

## A. Before testing

1. Plug the Vgate adapter into OBD-II.
2. Ignition ON or engine running.
3. Fully close/disconnect Car Scanner so it does not own the adapter simultaneously.
4. Turn Bluetooth on and grant the dashboard Bluetooth permissions when requested.
5. Keep the car stationary for the initial connection test.

## B. Visual / lifecycle

1. Launch app; verify 1280×720 layout fits with no grey/black masks over data fields.
2. Switch Street → Sport → Diagnostics repeatedly; geometry and tab height must not jump.
3. Street active tab: green glow; Sport: red; Diagnostics: cyan.
4. Put app in background and return; there must be no crash or duplicated connection task.
5. Turn screen off/on once and verify reconnect behavior is controlled.

## C. Connection Manager

1. Open Diagnostics → OBD Connection.
2. Test BT 3.0 list and Scan.
3. Test BLE list and Scan.
4. Verify devices show name/address/transport and list can scroll.
5. Select the exact Vgate endpoint that works in Car Scanner.
6. Connect; expected stages: Adapter found → Connecting → Initializing → ECU handshake → Connected.
7. Disconnect must immediately stop the session and cancel reconnect.
8. Toggle Auto reconnect and verify the preference persists after app restart.
9. For Wi‑Fi, edit host/port and confirm the values persist.

## D. Honest data behavior

Before ECU handshake:
- RPM/speed/temperatures/load/throttle/fuel/voltage = `--`.
- A bitmap-confirmed unsupported PID = `N/A`.
- No random values are allowed.

After ECU handshake:
- RPM at idle follows the real engine.
- Light throttle makes RPM and the Sport tachometer move together.
- Speed follows vehicle movement and returns to zero when stopped.
- Coolant changes slowly and plausibly.
- Throttle/load react to pedal input.
- ECU module voltage and adapter voltage are not mislabeled as the same source.

## E. Diagnostics truthfulness

1. DTC must show Not available / Not read until Mode 03 completes successfully.
2. `No fault codes` is allowed only after a valid empty Mode 03 result.
3. Readiness monitors must show Complete / Incomplete / N/A from real PID 0101 bits.
4. Do not test Clear DTC while driving; the app must never issue Mode 04 automatically.

## F. Failure/reconnect cases

Test and photograph the exact status if any fails:
- Bluetooth off
- Permission denied
- Adapter out of range/unplugged
- Ignition OFF
- Adapter transport connects but ELM does not answer
- ELM answers but ECU does not return valid 41 00 bitmap
- Link lost while live
- Reconnect after ignition cycle

## G. Build verification after GitHub Actions

After a green workflow run verify:
- artifact name `Civic-FA1-Dashboard-v0.6.0-APK`
- versionName `0.6.0`
- versionCode `16`
- APK installs over the previous debug build if signed with the same debug key in CI environment; otherwise uninstall previous build first
