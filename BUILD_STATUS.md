# Build status — v0.7.1

## Verified in this workspace
- Project structure intact.
- `ObdProtocol.java` compiles with JDK 17.
- `SensorFreshness.java` compiles with JDK 17.
- Dashboard/OBD source brace and parenthesis balance checked after the v0.7.1 patch.
- Runtime Java contains no `Random` telemetry path.
- Runtime backgrounds remain decorative-only; reference screenshots are stored only under `design-reference/`.
- Version bumped to 0.7.1 / code 18.
- GitHub Actions artifact name updated to `Civic-FA1-Dashboard-v0.7.1-APK`.

## Environment limitation
This workspace does not contain Android SDK / android.jar, so `:app:assembleDebug` and `apksigner verify` cannot be executed locally here. GitHub Actions remains the authoritative Android compile/test step.

## Real-car status
Not yet validated on the vehicle. v0.7.1 specifically adds the Vgate 18F0/2AF0/2AF1 BLE profile and scan-error-2 recovery so the next head-unit test can either connect or produce a useful GATT trace in View Log.
