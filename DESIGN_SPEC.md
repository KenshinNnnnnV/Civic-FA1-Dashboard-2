# Civic FA1 Dashboard — v1.0 UI design lock

Target: 1280×720 landscape Android head unit.

## Header
- Same physical geometry in CONNECT / SPORT / DIAGNOSTICS.
- Left: Honda mark + `CIVIC FA1` + `i-VTEC 1.8L R18A`.
- Center mode title/subtitle:
  - CONNECT MODE / OBD SETUP & LINK
  - SPORT MODE / HIGHER STANDARDS
  - DIAGNOSTICS MODE / KNOW YOUR CAR
- Right: OBD icon + live OBD state + state subtitle.
- No clock.
- No Wi-Fi icon.
- Mode accent only:
  - CONNECT blue/cyan
  - SPORT red
  - DIAGNOSTICS green

## Bottom navigation
- Exactly three equal tabs.
- Same width, height, slant, icon placement and text placement in all modes.
- Inactive tabs: dark fill + neutral light outline.
- Active tab only:
  - CONNECT blue/cyan glow
  - SPORT red glow
  - DIAGNOSTICS green glow
- Switching mode must not move or resize the navigation.

## SPORT tachometer
- Scale 0..8 x1000 RPM.
- The colored arc itself is the RPM indicator.
- Unused scale remains dark.
- Active scale fills cumulatively:
  - 0..4000 RPM green
  - 4000..5500 RPM yellow
  - 5500..8000 RPM red
- Large center RPM number is the latest ECU sample, not a simulated/interpolated number.
- Arc animation may smooth visually toward the current sample.

## Data behavior
- Missing/stale: `--`
- Unsupported: `N/A`
- No fake sensor values.
