# Build status — v0.9.4

Prepared for GitHub Desktop -> Commit -> Push -> GitHub Actions.

Validated locally in this workspace:
- AndroidManifest.xml parses successfully.
- No boot/autostart receivers or schedulers found.
- Java structural delimiter checks pass for all source files.
- ObdProtocol.java and SensorFreshness.java compile with JDK.
- OBD parser/freshness smoke tests pass.
- Runtime dashboard PNGs are RGB 1280x720.
- SPORT disconnected background has a neutral tachometer arc.
- SPORT runtime arc contains green/yellow segments only (no red segment).
- MainActivity background path does not call ObdManager.suspend()/disconnect().

Full Android APK compilation is delegated to the included GitHub Actions workflow because this workspace does not contain a local Android SDK/Gradle installation.
