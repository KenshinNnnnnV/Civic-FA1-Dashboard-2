# BUILD STATUS — v1.0.3

Source package prepared for GitHub Desktop / GitHub Actions.

Runtime resources verified:
- app/src/main/res/drawable-nodpi/background_connect.png — new Connect dashboard
- app/src/main/res/drawable-nodpi/background_sport.png — new Sport dashboard
- app/src/main/res/drawable-nodpi/background_diagnostics.png — new Diagnostics dashboard

Behavior retained:
- manual app launch (no self-start on head-unit boot)
- BLE / Classic / Wi-Fi OBD code from previous working base
- dynamic Sport RPM arc, no needle
- tappable/persistent Sport sensor widgets

Local APK was not built in this environment because Android SDK/Gradle runtime is not installed here. GitHub Actions workflow is configured to build the debug APK.
