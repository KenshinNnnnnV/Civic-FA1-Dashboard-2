# Civic FA1 Dashboard v0.6.0

Android dashboard for Honda Civic FA1 2011 / R18A 1.8, designed for a 1280×720 landscape UIS8581A head unit with 2 GB RAM.

## Core rules

- No random/fake telemetry in normal mode.
- Before a verified ECU response, live fields show `--`.
- A PID confirmed unsupported by the ECU capability bitmap is shown as `N/A`.
- Bluetooth/GATT transport connection is not treated as ECU connection.
- `OBD: CONNECTED` is shown only after a complete valid `41 00 A B C D` ECU reply.
- Street, Sport and Diagnostics share one telemetry model and one fixed 1280×720 geometry.
- Runtime backgrounds contain decorative city/car artwork only; cards, gauges, labels, bars and values are native Canvas UI.

## OBD transports

Implemented connection paths:

- Bluetooth Classic / SPP (secure with insecure fallback)
- BLE for explicitly implemented UART profiles only
- Wi‑Fi ELM327 with editable host/port
- AUTO only reconnects to a previously ECU-verified adapter; it does not guess an adapter by name

The Vgate iCar Pro BLE 4.0 DUAL is known to work with the vehicle through Car Scanner. If Civic FA1 Dashboard cannot connect, treat that as an application/transport issue and use the debug log rather than assuming the adapter is faulty.

## Live OBD-II data

The app discovers supported Mode 01 PID bitmaps before regular polling. Implemented values include:

- 010C RPM
- 010D Vehicle speed
- 0105 Coolant temperature
- 0111 Throttle position
- 0104 Calculated engine load
- 010F Intake air temperature
- 0110 MAF
- 010B MAP
- 010E Ignition timing
- 0106 Short-term fuel trim
- 0107 Long-term fuel trim
- 012F Fuel level
- 0142 ECU/control-module voltage
- 015E Engine fuel rate
- ATRV Adapter voltage (kept separate from ECU voltage)
- 0101 Readiness monitors
- Mode 03 stored DTCs

Mode 04 clear-DTC is not issued automatically.

## Reliability changes in v0.6.0

- Session/generation isolation prevents stale callbacks from modifying a newer connection.
- Classic Bluetooth sockets are published before blocking connect and can be closed on timeout/cancel.
- BLE waits for successful CCCD descriptor write before starting ELM initialization.
- BLE characteristic selection is restricted to coherent UART pairs within supported services.
- ELM commands are serialized; a timeout without prompt `>` is not accepted as a valid partial response.
- Strict line-based OBD parsing replaces arbitrary hex extraction.
- Supported-PID bitmap discovery includes continuation pages.
- Temporary `NO DATA` does not permanently blacklist a supported PID.
- Per-sensor freshness TTL clears stale values to `--`.
- Readiness distinguishes unsupported / incomplete / complete monitors.
- DTC state distinguishes not-read / error / no-codes / codes-present.
- Polling is priority scheduled so RPM/speed/throttle/load are refreshed more often than slow sensors.
- Connection Manager supports scan/select/connect/disconnect/retry, scrolling, auto reconnect and editable Wi‑Fi endpoint.
- Activity lifecycle explicitly suspends hardware connections in background and closes resources on destroy.
- Debug logging is bounded and disabled by default.

## Build

GitHub Actions uses Java 17, Gradle 8.9, Android SDK 34 and runs unit tests before `:app:assembleDebug`.

Artifact name:

`Civic-FA1-Dashboard-v0.6.0-APK`

This archive is intended to be copied into the repository root:

`KenshinNnnnnV/Civic-FA1-Dashboard-2`
