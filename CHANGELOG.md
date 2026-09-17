# v0.8.2 — manual launch / task behavior

- Dashboard remains full-screen only while the user has it open.
- Leaving the app (Home / app switch / background) closes and removes its task.
- Prevents FYT/UIS8581A from restoring the dashboard as the last foreground task after sleep/ACC wake.
- Launcher task is excluded from Recents.
- No BOOT_COMPLETED receiver, overlay permission, foreground service, or auto-start component is added.
- Approved CONNECT / SPORT / DIAGNOSTICS design is unchanged.
- OBD auto-reconnect behavior inside a manually opened dashboard is unchanged.

# Changelog

## v0.8.1 — disconnected visual lock

### Approved visual state
- Replaced all three runtime artwork files with the final disconnected-state screens explicitly approved in chat.
- CONNECT / SPORT / DIAGNOSTICS remain the same design; no new visual concept was introduced.
- SPORT and DIAGNOSTICS preserve the locked no-OBD artwork directly while disconnected, preventing a second approximate text pass from changing the approved look.
- Header disconnected state is red and matches the approved references.
- CONNECT no-adapter/ECU fields now use `Not detected`, `--`, `Disconnected` / `Unavailable`, a red X and `Pending` stage labels.
- DIAGNOSTICS disconnected presentation is `WAITING FOR ECU` with truthful `--` / `N/A`.

### Connection UI semantics
- Separated preferred connection type (BLE / BT / Wi-Fi) from AUTO transport policy so the UI can match the approved reference: BLE can be the preferred type while `Auto (Recommended)` is selected below.
- Selecting a concrete recent device switches out of AUTO; selecting AUTO restores verified-adapter automatic behavior.

### Build
- versionCode 20 / versionName 0.8.1.
- GitHub Actions artifact: `Civic-FA1-Dashboard-v0.8.1-APK`.

## v0.8.0 — approved design lock

### Visual correction
- The three user-approved 1280×720 references are now the locked runtime visual shell.
- Removed the v0.7-style reconstruction of the whole UI with generic Canvas panels.
- The approved header, city/car composition, card geometry and bottom navigation are rendered 1:1.
- Runtime code replaces only dynamic/example values and interactive states.
- Re-measured CONNECT, SPORT and DIAGNOSTICS hit zones from the approved images.
- Removed the possibility of a separate fake shift-light strip; Sport keeps the approved tachometer composition.
- Sport default widgets retain the approved visual layout; custom sensor selection remains available.

### Data integrity
- Reference numbers such as 3250 RPM, 72 km/h, 14.1 V and simulated diagnostics are not used as telemetry.
- Disconnected/stale data stays `--`; unsupported PIDs stay `N/A`.
- Header OBD status and clock are live.

### OBD scope
- v0.7.1 transport/protocol fixes are preserved.
- No additional speculative Bluetooth workaround is added in v0.8.0 while the UIS8581A head-unit Bluetooth limitation is being diagnosed separately.

### Build
- versionCode 19 / versionName 0.8.0.
- GitHub Actions artifact: `Civic-FA1-Dashboard-v0.8.0-APK`.

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
