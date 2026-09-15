# Civic FA1 Dashboard v0.7.0 — real-car test checklist

Target: Honda Civic FA1 2011, R18A 1.8L, Vgate iCar Pro BLE 4.0 DUAL, 1280×720 Android head unit.

## A. Installation / launch
1. Install the debug APK from GitHub Actions.
2. Launch in landscape.
3. Confirm full-screen 1280×720 geometry, no system-scale jump between tabs.
4. Confirm initial page is CONNECT.
5. Confirm no random RPM/speed/temperature values appear while disconnected.

## B. CONNECT layout
1. Confirm all nine Connect cards fit without overlap.
2. Confirm no Live Data Preview card exists.
3. Confirm bottom CONNECT / SPORT / DIAGNOSTICS buttons are identical in size and position.
4. Confirm only CONNECT has green active glow.

## C. Bluetooth permissions
1. Fresh install: permission should not be requested before Bluetooth scan/connect is used.
2. Tap BLE 4.0 or Scan; verify explanation appears before the Android permission prompt.
3. Deny permission: app must remain usable and show permission-required state.
4. Grant permission: scan again.

## D. BLE adapter discovery
1. Ignition ON; Vgate plugged in.
2. Select BLE 4.0.
3. Tap Scan.
4. Confirm discovered BLE devices appear with name/address where available.
5. Select the actual Vgate adapter.
6. Tap Connect.
7. Expected sequence: adapter found → connecting → ELM initializing → ECU handshake → connected.
8. Verify ELM initialization does not begin before BLE CCCD subscription succeeds.

## E. Bluetooth Classic fallback
1. If the adapter exposes a usable Classic side, select BT 3.0.
2. Scan / select a paired adapter.
3. Connect and verify cancel/disconnect can interrupt a pending connection.
4. Confirm UI never reports ECU connected merely because RFCOMM connected.

## F. ECU handshake
1. Confirm CONNECTED appears only after valid Mode 01 `41 00 A B C D`.
2. Turn ignition off or remove adapter: live values must become `--` after freshness timeout.
3. Reconnect and confirm stale callbacks from the previous session do not change current state.

## G. Live telemetry
With engine running, verify:
- RPM follows throttle quickly.
- Vehicle speed stays 0 while stationary and changes when driving.
- Throttle and load react quickly.
- Coolant / intake update more slowly.
- Module Voltage is PID 0142; Adapter Voltage is ATRV and is separately named.
- Unsupported PID is `N/A`, not a fabricated value.

## H. SPORT mode
1. Open SPORT.
2. Confirm tachometer, speed and cards do not overlap.
3. Confirm one RPM source drives digital RPM, gauge fill and shift lights.
4. Tap each of the five secondary cards; Sensor Picker must open.
5. Select different sensors and restart app; choices must persist.
6. Verify fixed tachometer and speed cannot be replaced.

## I. DIAGNOSTICS mode
1. Confirm there are no connection controls on this page.
2. Check top metric cards use real telemetry.
3. DTC panel: before successful Mode 03 it must not say NO FAULT CODES.
4. After valid Mode 03 zero-code response, NO FAULT CODES is allowed.
5. If codes exist, confirm multiple codes are shown.
6. Readiness: unsupported monitors are not counted as complete.
7. Verify MIL/DTC count and readiness summary match PID 0101.
8. Live sensor table should contain real supported/useful values and be vertically scrollable.

## J. Reconnect / lifecycle
1. Enable Auto reconnect.
2. Disconnect adapter temporarily and reconnect it.
3. Background/foreground the app.
4. Cycle ignition.
5. Confirm no duplicate connection sessions, zombie sockets or stale GATT callbacks.
6. Manual Disconnect must stop the current connection and not instantly reconnect in the same user action.

## K. Debug log
1. Tap View Log (this explicitly enables bounded debug logging).
2. Reproduce a connection attempt.
3. Confirm state transitions and ELM command/response summaries appear.
4. Tap Export and note the path shown by the app.

## L. Acceptance criteria
Do not accept the build if any of these occur:
- fake/random values in normal mode;
- duplicate/baked-in numbers;
- overlap between gauge/cards/navigation;
- ECU CONNECTED before a real ECU response;
- NO FAULT CODES without successful Mode 03;
- readiness complete without real readiness data;
- old Street tab returns;
- different layout scaling between modes.
