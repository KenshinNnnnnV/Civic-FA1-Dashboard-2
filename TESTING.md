# v0.8.4 testing checklist

## Build
- GitHub Actions unit tests green.
- `:app:assembleDebug` green.
- Artifact name is `Civic-FA1-Dashboard-v0.8.4-APK`.

## First visual test — OBD physically disconnected
### CONNECT
- approved composition is unchanged;
- red `OBD: DISCONNECTED`; no invented adapter/session data.

### SPORT
- center tachometer/cards keep approved geometry;
- static green/yellow/red RPM band is visually neutral/dark;
- no separate RPM needle/marker;
- center RPM, speed and sensor values are `--`;
- SPORT active tab does not rise above CONNECT/DIAGNOSTICS; only restrained red active styling remains.

### DIAGNOSTICS
- approved 6 + 3 + 3 structure remains;
- `WAITING FOR ECU`, `--` and `N/A` remain truthful.

## Connected SPORT test
- center RPM is the latest real ECU number;
- arc moves smoothly between real samples without changing the numeric value;
- 0–2500 RPM lights green only;
- 2500–4500 adds yellow after the green segment;
- above 4500 adds red after green+yellow;
- range above current RPM stays dark;
- rapid throttle/RPM changes do not freeze navigation or produce duplicate overlays.

## Performance test on UIS8581A
- switch CONNECT -> SPORT -> DIAGNOSTICS repeatedly for 2–3 minutes;
- no progressive slowdown or obvious GC pauses;
- live values continue updating while SPORT arc animates;
- Home/app switcher minimizes normally and returning resumes/reconnects;
- stale values still expire to `--`; unsupported remains `N/A`.

## OBD integrity
- no automatic Mode 04;
- valid ECU handshake is still required before ECU_CONNECTED;
- DTC/readiness success states require real responses;
- module voltage PID 0142 remains separate from ELM ATRV.
