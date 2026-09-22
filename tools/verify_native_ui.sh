#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main"
fail(){ echo "VERIFY FAILED: $*" >&2; exit 1; }
[ ! -f "$SRC/java/com/civicfa1/dashboard/DashboardView.java" ] || fail "legacy DashboardView.java present"
! grep -RInq 'refPatch(' "$SRC/java" || fail "refPatch found"
for n in background_connect background_sport background_diagnostics background_street mode_street mode_sport mode_diagnostics; do
  ! find "$SRC/res" -type f -name "$n.*" | grep -q . || fail "legacy runtime artwork $n present"
done
! grep -RInEq 'BOOT_COMPLETED|RECEIVE_BOOT_COMPLETED' "$SRC/AndroidManifest.xml" || fail "boot auto-start permission/receiver found"
grep -q 'new DashboardRootView(this)' "$SRC/java/com/civicfa1/dashboard/MainActivity.java" || fail "MainActivity not using DashboardRootView"
grep -q 'versionName "1.1.0"' "$ROOT/app/build.gradle" || fail "wrong versionName"
echo "NATIVE_UI_INVARIANTS_PASS"
