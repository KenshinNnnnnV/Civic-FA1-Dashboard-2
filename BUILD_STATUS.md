# Build status for v0.6.0

Source base audited: `KenshinNnnnnV/Civic-FA1-Dashboard-2`, commit `c0f40936865a4eb3d0b362a2f7ebc2117e56be88`.

Checks completed in the preparation environment:

- project/resource structure checked;
- no runtime references remain to old `mode_street/mode_sport/mode_diagnostics` baked-data images;
- Java parser-oriented `javac` pass found no Java syntax-class errors before Android symbol resolution;
- pure-Java `ObdProtocol.java` compiles with `javac`;
- standalone protocol harness passed echo filtering, malformed text rejection, prompt detection, supported-PID bitmap, short bitmap rejection and Mode 03 P0300 decoding;
- archive integrity checked after packaging.

A full Android `assembleDebug` cannot be executed in this preparation container because Android SDK/Gradle tooling is not installed here. The included GitHub Actions workflow runs unit tests and then `:app:assembleDebug` using SDK 34 / Java 17 / Gradle 8.9.
