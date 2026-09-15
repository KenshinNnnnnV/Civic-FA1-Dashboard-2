# v0.6.0 → v0.7.0 audit summary

Audit basis: current `KenshinNnnnnV/Civic-FA1-Dashboard-2` v0.6 source copied into this package before the UI rewrite.

## Preserved OBD work

The v0.6 source already contained the important architecture from the previous static audit, so it was retained rather than replaced:

- Session generation / ownership and stale-callback checks.
- Bluetooth Classic socket published before blocking `connect()` and timeout closure.
- BLE waits for CCCD descriptor write before ELM session start.
- BLE profile selection is constrained to known UART service/characteristic combinations rather than arbitrary cross-service characteristics.
- AUTO reconnect uses only saved ECU-verified adapter information.
- ECU connection requires a valid Mode 01 PID 00 payload with four bitmap bytes.
- Supported PID continuation pages are read before polling.
- NO DATA does not permanently blacklist a bitmap-supported PID.
- Line-oriented ELM parser keeps text statuses separate from hexadecimal frames.
- Command path requires prompt completion and resynchronizes/retries on timeout.
- Per-sensor timestamps/freshness exist in telemetry.
- Readiness uses B support/incomplete + C support + D incomplete and spark/compression layout.
- Stored DTC has explicit NOT_READ / ERROR / NO_CODES / HAS_CODES states.
- PID 0142 module voltage and ATRV adapter voltage are distinct.
- Priority polling is sequential; no parallel ELM command flood.

## Problems found in the v0.6 product layer

- The old UI still had STREET / SPORT / DIAGNOSTICS instead of the final CONNECT / SPORT / DIAGNOSTICS product structure.
- The v0.6 drawing geometry did not match the approved references and produced the overlap seen on the real 1280×720 head unit.
- OBD controls were mixed into Diagnostics through a secondary panel instead of having a dedicated Connect page.
- Sport secondary metrics were fixed rather than user-selectable.
- UI sensor state was inferred ad hoc and did not expose an explicit VALID / STALE / UNSUPPORTED / NOT_AVAILABLE classification.
- Connection timeout in the UI was not applied to the transport code.
- Low Power Mode / Auto-detect Protocol settings were visually present but not connected to scheduler/init behavior.
- Debug log did not include all state transitions.

## v0.7 response

v0.7 replaces the product UI while retaining the audited transport/protocol code, wires the above settings into behavior, adds explicit sensor freshness state, and makes the three approved reference images the design specification rather than runtime screenshots.
