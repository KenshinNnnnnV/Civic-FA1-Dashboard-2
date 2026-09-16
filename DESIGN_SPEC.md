# Civic FA1 Dashboard v0.8.1 — LOCKED APPROVED DESIGN

The three files in `design-reference/approved-v0.8.1/` are the visual source of truth for the no-OBD state. They are the exact screens approved in chat after the visual comparison. Do not redesign, restyle, add gauges, add extra bars, change card proportions, or reinterpret the composition.

## Rendering contract
- Logical canvas: exactly 1280×720.
- Runtime backgrounds use the locked approved disconnected-state shell for CONNECT / SPORT / DIAGNOSTICS.
- While OBD is disconnected, SPORT and DIAGNOSTICS preserve the locked artwork directly; the app only replaces the clock. This avoids approximating the approved no-data typography with a second drawing pass.
- CONNECT masks all example device/session/settings areas that must be functional so no fake adapter or device is presented as real.
- When live state/data changes, only the relevant values/status regions are replaced in place.
- There is no gray value plate and no second number painted over an old number.
- Disconnected/missing/stale telemetry = `--`.
- Unsupported PID = `N/A`.
- Dynamic values remain inside the exact approved widget geometry.

## CONNECT
- Keep the large city/Civic artwork zone intact above the cards.
- Top: OBD Adapter / ECU Link / Connection Type.
- Middle: Device Actions / Settings / Connection Status.
- Bottom: Recent Devices / Transport Options / Connection Diagnostics.
- Header disconnected state: red `OBD: DISCONNECTED`, `ALL SYSTEMS OFFLINE`.
- No adapter: `Not detected`, transport `--`, firmware/address `--`.
- ECU unavailable: protocol/response/VIN/type `--`, status `Unavailable`.
- Connection Status shows the red X and `Not Connected` state.
- Connection Diagnostics uses `Pending` before each stage succeeds.
- The approved visual shows BLE 4.0 in the top Connection Type while `Auto (Recommended)` may be active in Transport Options. v0.8.1 therefore stores AUTO policy separately from the preferred scan/connection type.

## SPORT
- Central tachometer, colored ring, Civic artwork and card framing remain exactly as approved.
- No extra shift-light strip.
- Without ECU data the locked screen itself shows `--` for RPM, speed and all five sensor cards.
- With live data, values replace the placeholders in the same positions.
- Five secondary cards remain selectable; the center RPM/speed instrument remains fixed.

## DIAGNOSTICS
- Keep the exact approved hierarchy: 6 top metrics; DTC / Readiness / Live Sensor Data; Freeze Frame / Emissions / ECU Information.
- Without ECU data the locked screen displays `WAITING FOR ECU`, truthful `--`/`N/A`, and no simulated successful diagnostics.
- `NO FAULT CODES` appears only after a valid Mode 03 zero-code response.
- Readiness comes only from PID 0101.
- Freeze Frame remains not available until actual Mode 02 support exists.

## Non-negotiable rules
1. Do not redraw the overall design from imagination.
2. Do not crop/stretch the 1280×720 shell.
3. Do not add cyan/blue framing absent from the locked references.
4. Do not change bottom-tab proportions or mode structure.
5. Do not add sample/random telemetry.
6. Do not overlay a new number on a visible old number.
7. OBD connection work must not be allowed to alter the visual design.
