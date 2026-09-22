$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

$Legacy = @(
    "app/src/main/java/com/civicfa1/dashboard/DashboardView.java",
    "app/src/main/res/drawable-nodpi/background_connect.png",
    "app/src/main/res/drawable-nodpi/background_sport.png",
    "app/src/main/res/drawable-nodpi/background_diagnostics.png",
    "app/src/main/res/drawable-nodpi/background_street.jpg",
    "app/src/main/res/drawable-nodpi/mode_street.jpg",
    "app/src/main/res/drawable-nodpi/mode_sport.jpg",
    "app/src/main/res/drawable-nodpi/mode_diagnostics.jpg"
)

Write-Host "Civic FA1 Dashboard v1.1.0 - legacy cleanup" -ForegroundColor Cyan
foreach ($Relative in $Legacy) {
    $Path = Join-Path $Root $Relative
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Force
        Write-Host "REMOVED  $Relative"
    } else {
        Write-Host "OK       $Relative"
    }
}

$Required = @(
    "app/src/main/java/com/civicfa1/dashboard/DashboardRootView.java",
    "app/src/main/java/com/civicfa1/dashboard/ConnectScreenView.java",
    "app/src/main/java/com/civicfa1/dashboard/SportScreenView.java",
    "app/src/main/java/com/civicfa1/dashboard/DiagnosticsScreenView.java",
    "app/src/main/java/com/civicfa1/dashboard/MainActivity.java",
    "app/src/main/res/drawable-nodpi/splash_bg.jpg"
)

$Missing = @()
foreach ($Relative in $Required) {
    if (-not (Test-Path -LiteralPath (Join-Path $Root $Relative))) {
        $Missing += $Relative
    }
}

if ($Missing.Count -gt 0) {
    Write-Host "" 
    Write-Host "ERROR: new package was not copied completely. Missing:" -ForegroundColor Red
    $Missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host ""
Write-Host "CLEANUP PASS - repository is ready for GitHub Desktop review/commit." -ForegroundColor Green
