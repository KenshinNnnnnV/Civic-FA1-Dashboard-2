# Build status — v0.9.3

Prepared full source package for `KenshinNnnnnV/Civic-FA1-Dashboard-2`.

## Verified in this environment
- v0.9.3 was applied on top of the exact v0.8.3 project snapshot matching the current GitHub `main` assets/source hashes checked during preparation.
- All three locked runtime artwork files remain 1280×720 and are unchanged.
- Pure-Java `ObdProtocol.java` and `SensorFreshness.java` compile with Java 17 language level.
- Modified Java files have balanced delimiters and pass source-level sanity checks; no simulator/random telemetry was added.
- RPM arc smoothing is visual-only; the numeric RPM value remains the latest ECU sample.
- Release ZIP SHA-256 and source checksums are generated after packaging.

## Android build confirmation
This environment does not contain the Android SDK/Gradle toolchain used by the repository workflow. The authoritative build is GitHub Actions after upload:
1. `:app:testDebugUnitTest`,
2. `:app:assembleDebug`,
3. artifact `Civic-FA1-Dashboard-v0.9.3-APK`.

Do not call the APK verified until that Actions run is green.
