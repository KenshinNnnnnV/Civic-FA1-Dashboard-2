# Civic FA1 Dashboard v0.3.4

Android dashboard for Honda Civic FA1 (1280x720 landscape).

## v0.3.4 changes

- Dynamic values are no longer hidden by black masks in code.
- Street: speed, RPM, coolant, voltage, fuel, Trip A, fuel economy, trip time, average speed and load are live UI values.
- Sport: RPM, speed, coolant, voltage, throttle, load and intake are live UI values.
- Sport tachometer arc and upper shift-light row react to the same RPM value.
- Sport background geometry is normalized to the same 1280x720 canvas as Street and Diagnostics.
- Diagnostics live values are drawn from one telemetry model.
- Initial real OBD connection layer added for Vgate/ELM327:
  - paired Bluetooth Classic SPP auto-detection;
  - BLE scan and generic UART characteristic auto-detection;
  - ELM327 initialization;
  - polling for RPM, speed, coolant, throttle, engine load, intake temperature, MAF, fuel level and adapter voltage.
- If no compatible adapter connects, the dashboard remains in Simulator mode.

## OBD connection

1. Plug the Vgate iCar Pro into the car OBD-II port.
2. Turn ignition ON.
3. Enable Bluetooth on the Android head unit.
4. If the adapter is Bluetooth Classic, pair it in Android first if necessary.
5. Open the dashboard and allow Bluetooth permission.
6. Tap the OBD status at the top, or the OBD CONNECTION card in Diagnostics.
7. Watch the status progress: SEARCHING -> FOUND -> CONNECTING -> CONNECTED.

Vgate iCar Pro exists in several radio versions. v0.3.4 tries both Bluetooth Classic and BLE, but final compatibility must be verified on the actual adapter/head unit pair.

## GitHub build

Push the project contents to the repository root. GitHub Actions builds:

`Civic-FA1-Dashboard-v0.3.4-APK`

APK path inside the workflow:

`app/build/outputs/apk/debug/app-debug.apk`
