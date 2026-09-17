# v0.8.1 testing checklist

## Build
- GitHub Actions unit tests green.
- `:app:assembleDebug` green.
- Artifact `Civic-FA1-Dashboard-v0.8.1-APK`.

## First visual test — OBD physically disconnected
Compare the head-unit photo to `design-reference/approved-v0.8.1/`.

### CONNECT
- exact city/Civic/header/card composition;
- red `OBD: DISCONNECTED`;
- no invented adapter discovery;
- `Not detected` / `--` / `Unavailable`;
- red X + `Not Connected`;
- stage list shows `Pending`;
- top BLE preference and bottom AUTO policy can coexist as in the reference.

### SPORT
- exact central tachometer and five card geometry;
- no extra shift strip;
- RPM/speed/sensors are `--`;
- no gray replacement plates or duplicate digits.

### DIAGNOSTICS
- exact 6 + 3 + 3 panel structure;
- `WAITING FOR ECU`;
- live list is `--`; ECU/system fields are `N/A`;
- no simulated success states.

## Connected-state rules (when head-unit transport is eventually solved)
- values replace placeholders in place;
- stale -> `--`; unsupported -> `N/A`;
- no automatic Mode 04;
- DTC/readiness success states require real ECU responses.
