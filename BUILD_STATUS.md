# Build status — v0.8.1

Prepared source package for `KenshinNnnnnV/Civic-FA1-Dashboard-2`.

## Verified in this environment
- All three locked runtime reference images are exactly 1280×720 PNG.
- Pure-Java `ObdProtocol.java` and `SensorFreshness.java` compile with JDK 17.
- Full Java source was passed through `javac`; Android SDK symbols are unavailable here, but no Java syntax-style errors were reported before Android API resolution failures.
- Runtime source contains no random/simulator telemetry generator.
- Release ZIP integrity and SHA-256 are checked after packaging.

## Still authoritative
This container has no Android SDK / `android.jar`. The real Android build must be confirmed by GitHub Actions:
1. unit tests,
2. `:app:assembleDebug`,
3. artifact `Civic-FA1-Dashboard-v0.8.1-APK`.

Do not call the APK verified until Actions is green.
