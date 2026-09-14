# v0.5.0 road-test checklist

## Before launch

1. Plug Vgate iCar Pro into the OBD-II socket.
2. Ignition ON or engine running.
3. In Android Bluetooth settings pair the Android-side adapter endpoint (commonly `Android-Vlink`; common PIN `1234`).
4. Make sure Car Scanner is fully disconnected/closed before testing Civic FA1 Dashboard. Only one app should own the adapter link at a time.

## Connect in Civic FA1 Dashboard

1. Open **Diagnostics**.
2. Tap **OBD CONNECTION**.
3. Select **BT 3.0**.
4. Tap **REFRESH**.
5. Select the paired Vgate / Android-Vlink device.
6. Tap **CONNECT SELECTED**.

Expected status sequence:

`ADAPTER FOUND` → `CONNECTING` → `INITIALIZING` → `ECU` → `OBD: CONNECTED`

No sensor value is fabricated. Until the ECU has responded, all live fields remain `--`.

## Verify live values

- Engine off / ignition on: RPM should be 0 or unavailable; voltage should be plausible.
- Engine running at idle: RPM should be stable around actual idle.
- Light throttle: RPM and Sport tachometer should rise together.
- Drive slowly: speed should follow the vehicle and return to 0 when stopped.
- Coolant should change slowly, not jump.
- Throttle and load should react to accelerator input.

## If connection fails

Photograph the exact status line shown by the app. The message distinguishes:

- Bluetooth permission missing
- paired adapter not found
- Bluetooth serial link failure
- ELM327 did not respond
- adapter connected but ECU did not answer
- live link lost

Also note the adapter name shown in Android Bluetooth settings.
