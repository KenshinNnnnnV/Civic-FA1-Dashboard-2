# Changelog

## v0.7.1 — reference-match + Vgate BLE recovery

### UI
- Recalibrated all three screens against the approved 1280×720 reference images.
- Header reduced to 60 px and aligned to the reference.
- Connect page card geometry now follows the reference grid instead of the oversized v0.7.0 layout.
- Connect background is intentionally much darker; decorative city/car art no longer competes with cards.
- Sport tachometer enlarged and repositioned; the extra top shift-light row from v0.7.0 was removed because it is not present in the final approved reference.
- Sport card geometry was recalibrated to keep the large side cards and three lower sensor cards clear of the tachometer.
- Diagnostics top art is darkened/green-tinted and all diagnostic panels were moved to match the reference composition.
- Panel opacity increased and border color changed from bright blue to dark OEM teal/gray.
- Honda header mark replaced with a vector-style badge instead of the previous square H placeholder.
- Added normalized 1280×720 reference images to `design-reference/` so future work uses the same coordinate space as the head unit.

### OBD / Vgate
- Added the Vgate iCar Pro / VLink BLE GATT profile reported on real iCar Pro BLE 4.0 hardware:
  - service `000018F0-0000-1000-8000-00805F9B34FB`
  - notify `00002AF0-0000-1000-8000-00805F9B34FB`
  - write `00002AF1-0000-1000-8000-00805F9B34FB`
- Added the Vgate vendor single-characteristic profile as an explicit secondary supported profile.
- BLE now connects with `TRANSPORT_LE` on API 23+ and requests high connection priority.
- BLE scanner retries Android scan error 2 (`APPLICATION_REGISTRATION_FAILED`) twice with cooldown.
- Classic Bluetooth discovery is cancelled before BLE scanning to reduce radio conflicts on vendor head units.
- Removed `neverForLocation` from `BLUETOOTH_SCAN` because some Android BLE stacks can filter advertisements when that assertion is used.
- GATT service/characteristic layout is recorded in the bounded connection log before profile selection.
- Minimal state/GATT connection trace is retained even when verbose raw ELM logging is disabled, so a failed real-car test produces useful evidence in `View Log`.

### Build
- versionCode 18
- versionName 0.7.1
- GitHub Actions artifact: `Civic-FA1-Dashboard-v0.7.1-APK`
