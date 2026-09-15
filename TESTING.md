# Civic FA1 Dashboard v0.7.1 — real-car test checklist

Target: Honda Civic FA1 2011 R18A, UIS8581A 1280×720 head unit, Vgate iCar Pro BLE 4.0 DUAL.

## 1. Visual acceptance before OBD
- Confirm Connect, Sport and Diagnostics all occupy the same 1280×720 geometry.
- Compare each screen with `design-reference/*_reference_1280.png`.
- Confirm there are no blue overlay blocks, duplicated/baked values, overlapping cards or mode-size jumps.
- Confirm Sport has no extra top row of fake shift lights.
- Confirm Sport sensor cards open the sensor picker and remember selections after restart.

## 2. Bluetooth permissions
- Open Connect.
- Select BLE 4.0 and tap Scan.
- The app should request Bluetooth access only if Android requires it.
- If scan fails, open `View Log`; the log must contain the scan error code.

## 3. BLE — primary test
- Engine ON.
- Adapter plugged into OBD port.
- Close Car Scanner and any other app that may hold the adapter.
- Tap BLE 4.0 -> Scan.
- Select the Vgate/VLink device shown by the head unit.
- Tap Connect.
- Expected sequence: adapter found -> GATT connected -> services discovered -> CCCD subscribed -> ELM initialization -> ECU handshake -> CONNECTED.
- If profile selection fails, `View Log` must show every GATT service/characteristic UUID.

## 4. Bluetooth Classic fallback
- If the head unit exposes `Android-Vlink`, pair it in Android Bluetooth settings if required; common Vgate PIN is 1234.
- In Connect choose BT 3.0.
- Scan / select the paired Vgate device.
- Tap Connect.
- Expected: SPP transport -> ELM initialization -> valid `41 00 A B C D` -> CONNECTED.

## 5. Live ECU validation
Only after CONNECTED:
- RPM rises with throttle.
- Speed remains 0 while stopped and changes while driving.
- Coolant is plausible and changes slowly.
- Throttle/load react to pedal input.
- Module voltage is PID 0142, not ATRV.
- Adapter voltage is shown only when that separate sensor is selected.

## 6. Diagnostics
- DTC starts as NOT READ until a valid Mode 03 read completes.
- `NO FAULT CODES` appears only after valid zero-code response.
- Readiness shows COMPLETE / INCOMPLETE / N/A from PID 0101.
- Unsupported sensors show N/A; stale/missing sensors show --.

## 7. Recovery
- Unplug adapter while connected; values must clear/stale and status must leave CONNECTED.
- Reinsert adapter; test Auto reconnect if enabled.
- Cycle ignition and verify no zombie GATT/socket remains.

## Evidence to send back after a failure
- Photo of Connect screen.
- `View Log` text or exported `civic-obd-debug.log`.
- Screenshot of Car Scanner connection settings showing which transport/device name works on this same head unit.
