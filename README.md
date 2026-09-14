# Civic FA1 Dashboard v0.5.0

Major product-oriented rewrite for a Honda Civic FA1 / R18A Android head unit (1280×720 landscape).

## What changed

- Real Canvas widgets replace baked sensor values and black/grey masking patches.
- One fixed 1280×720 virtual layout is used for Street, Sport and Diagnostics, so changing modes does not change aspect ratio or geometry.
- Street uses green active lighting, Sport red, Diagnostics cyan.
- No random telemetry. Before a verified ECU response, sensor fields show `--`.
- OBD state is only marked CONNECTED after a real Mode 01 ECU response.
- Android Vgate path prioritizes paired Bluetooth Classic / SPP adapters such as `Android-Vlink`.
- BLE fallback supports common FFF0/FFF1/FFF2 and FFE0/FFE1 UART-style profiles.
- Wi-Fi ELM327 fallback uses 192.168.0.10:35000.
- ELM327 session is serialised and timeout controlled: ATZ, ATE0, ATL0, ATS0, ATH0, ATAT1, ATSP0, ATI, 0100, then live polling.
- Live PIDs: RPM, speed, coolant, throttle, load, intake temperature, MAF, MAP, timing, fuel level, engine fuel rate (if supported), adapter voltage.
- Readiness (`0101`) and stored DTCs (`03`) are polled separately.
- Diagnostics has an integrated connection manager instead of permanent opaque overlays.
- Last selected adapter and auto-reconnect preference are persisted.

## Vgate iCar Pro BLE Dual on Android

For the hardware tested by the project owner, pair the Android-side endpoint in Android Bluetooth settings first. It is commonly named `Android-Vlink` and commonly uses PIN `1234`. In the dashboard open Diagnostics → OBD Connection and select BT 3.0 / the paired device. AUTO also prioritizes names such as Android-Vlink, VLink, Vgate, iCar, OBD and ELM.

## Build

GitHub Actions builds a debug APK with Java 17, Gradle 8.9 and Android SDK 34.

Artifact name: `Civic-FA1-Dashboard-v0.5.0-APK`

## Safety / truthfulness

The app does not fabricate live ECU values. Unsupported PIDs remain unavailable. Vehicle support is limited to data exposed by the ECU through standard OBD-II / ELM327 commands.
