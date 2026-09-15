# v0.7.1 corrective audit

This build is a corrective response to the real-head-unit test of v0.7.0.

## Confirmed v0.7.0 failures
- Installed UI did not visually match the approved Connect/Sport/Diagnostics references.
- Header/card spacing was too large and bright blue/teal borders dominated the composition.
- Sport contained an extra shift-light row not present in the final approved reference.
- Decorative art was vertically distorted because a 1280×720 bitmap was scaled into only the content rectangle.
- The Android head unit still failed to establish the requested Vgate connection.

## v0.7.1 UI corrections
- Geometry is recalibrated in a normalized 1280×720 coordinate space.
- Reference screenshots are also stored normalized to 1280×720 for direct pixel comparison.
- Native widgets remain dynamic; no screenshot values are used at runtime.
- Background artwork is drawn 1:1 and darkened so it no longer competes with the native UI.
- Connect uses the reference's non-uniform three-column grid.
- Sport tachometer/card geometry is rebuilt around the final reference and the extra shift-light row is removed.
- Diagnostics uses a dedicated upper decorative zone and compact diagnostic grid.

## v0.7.1 OBD corrections
The previous BLE implementation supported FFF0, FFE0 and Nordic UART layouts, but did not implement the Vgate iCar Pro 18F0/2AF0/2AF1 GATT layout. That is a concrete compatibility gap for this adapter family.

v0.7.1 adds explicit support for that profile, retains same-service property/CCCD validation, forces LE transport on API 23+, retries BLE scan registration error 2, and stores a minimal GATT/state trace for post-test diagnosis.

## Still requires vehicle validation
- Actual GATT layout exposed by this specific adapter firmware.
- Whether the user's working Car Scanner setup uses BLE or Classic `Android-Vlink` on this head unit.
- ECU handshake and real PID update rate on the Honda R18A.
