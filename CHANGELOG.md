# Changelog

## 0.6.0

Major reliability and OBD audit release based on the verified v0.5.0 repository state.

### OBD / transport
- Added per-connection Session objects and generation IDs to isolate stale callbacks/resources.
- BLE ELM startup now waits for successful CCCD subscription (`onDescriptorWrite`).
- BLE UART selection is limited to coherent, explicitly supported service/characteristic layouts.
- Bluetooth Classic socket is stored before blocking connect so disconnect/timeout can close it.
- Retained secure → insecure SPP fallback without blocking the UI thread.
- AUTO no longer chooses an adapter from name heuristics; it uses only a previously ECU-verified adapter.
- ECU handshake requires a complete valid `41 00 A B C D` response.
- Added supported PID bitmap discovery including continuation pages.
- Temporary communication errors no longer mark supported PIDs permanently unsupported.
- Strict response parser preserves line/message boundaries and rejects arbitrary mixed text as hex.
- Command timeout without prompt `>` aborts/retries instead of accepting partial data.
- Added per-sensor freshness timestamps / TTL.
- Corrected readiness decoding using B/C/D and spark/compression layouts.
- DTC states now distinguish not-read, read-error, no-codes and codes-present.
- Added ECU module voltage PID 0142; ATRV remains separately identified as adapter voltage.
- Added 0106/0107 fuel trims.
- Added sequential priority polling scheduler.

### Connection manager / lifecycle
- Device list uses stable transport/address keys and supports scrolling.
- Added Classic discovery and BLE scanning paths.
- Added Scan, Select, Connect, Disconnect/Retry flow and functional auto-reconnect preference.
- Wi‑Fi host/port are editable and persisted.
- Successful Classic, BLE and Wi‑Fi adapters can be saved only after a real ECU handshake.
- Added explicit Activity start/stop/destroy integration and old-session callback suppression.

### UI / performance
- Runtime artwork contains no baked cards or sensor values.
- Cards, labels, bars, gauges and status are native Canvas widgets.
- Removed old mask/cover approach and random telemetry.
- Fixed 1280×720 virtual geometry for all three modes.
- Equal-size bottom tabs; Street green, Sport red, Diagnostics cyan.
- Removed forced software layer.
- Cached long-lived clock formatter and static bitmap assets.
- Corrected Sport tachometer color-zone logic.

### Verification
- Added pure-Java protocol unit tests for echo/text filtering, malformed input, prompt handling, PID bitmaps and Mode 03 DTC parsing.
- GitHub Actions runs tests before the APK build.
