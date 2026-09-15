# Build status — v0.7.0

Source package is prepared for GitHub Desktop / GitHub Actions.

## Local verification performed in this environment
- Pure-Java `ObdProtocol.java` compiles with JDK 17.
- Pure-Java `SensorFreshness.java` compiles with JDK 17.
- Parser smoke tests for Mode 01, NO DATA and Mode 03 P0300 passed.
- Full Java source was parsed by `javac`; no syntax-style errors were reported before Android API resolution errors.
- Runtime resources contain clean decorative backgrounds; old `mode_*.jpg` screenshot backgrounds are not included.
- Source scan found no `Random`, `SIMULATOR` or `STREET` references in runtime Java/resources.

## Limitation of this environment
Android SDK / `android.jar` is not installed and external network access is unavailable, so a real local Android `assembleDebug` and `apksigner verify` cannot be honestly executed here.

The included GitHub Actions workflow is the required authoritative build step. It installs platform-tools, Android 34 and build-tools 34.0.0, runs unit tests, builds the debug APK and uploads `Civic-FA1-Dashboard-v0.7.0-APK`.
