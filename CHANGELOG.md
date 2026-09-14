# Changelog

## 0.5.0

- Rebuilt the visible dashboard as native Canvas widgets on one fixed 1280×720 coordinate system.
- Removed baked sensor values, black masks and grey data patches.
- Removed simulator/random telemetry from normal operation.
- Added truthful offline state: `--` until real ECU data exists.
- Rebuilt Street, Sport and Diagnostics from one design system.
- Equal-sized tabs: Street green, Sport red, Diagnostics cyan.
- Sport RPM dial and speed display are real live widgets.
- Reworked OBD manager around a serialized ELM327 command pipeline.
- Android AUTO mode now prioritizes paired Bluetooth Classic Vgate/Android-Vlink endpoints.
- Added BT Classic secure→insecure SPP fallback.
- Added common BLE FFF0/FFE0 UART profile support.
- Added Wi-Fi ELM327 path.
- ECU is only considered connected after a genuine Mode 01 response.
- Added real PID polling, readiness monitor polling and stored DTC polling.
- Added adapter selector and saved last-adapter preference.
