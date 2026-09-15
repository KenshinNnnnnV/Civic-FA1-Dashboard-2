# Civic FA1 Dashboard — fixed 1280×720 design specification

The files `design-reference/*_reference_1280.png` are visual specifications only. They are not runtime backgrounds.

## Shared
- Logical canvas: 1280×720.
- Header: y=0..60.
- Main content: y=60..632.
- Bottom navigation: y=632..716.
- CONNECT / SPORT / DIAGNOSTICS buttons use identical geometry.
- Dynamic values are always native/programmatic UI.

## Connect
Reference grid:
- Adapter: (2,68)–(428,226)
- ECU link: (436,68)–(834,226)
- Connection type: (842,68)–(1266,226)
- Device actions: (2,234)–(428,442)
- Settings: (436,234)–(792,442)
- Connection status: (800,234)–(1266,442)
- Recent devices: (2,450)–(428,628)
- Transport options: (436,450)–(834,628)
- Connection diagnostics: (842,450)–(1266,628)

## Sport
- Large tachometer center: x=640, y≈255.
- Large left sensor card: (30,292)–(384,449)
- Large right sensor card: (896,292)–(1250,449)
- Bottom cards: y=463..620
- No independent fake shift-light animation.
- RPM is the single source for digital RPM and gauge fill/color zones.
- Five secondary cards remain user-selectable.

## Diagnostics
- Decorative city/car art is confined visually to the upper zone; diagnostic data is native UI.
- Top metrics: y=222..320.
- Main diagnostic panels: y=330..520.
- Bottom diagnostic panels: y=530..626.
- No connection controls in Diagnostics.

## Non-negotiable rules
- No baked speed/RPM/temp/status/DTC/readiness values in runtime JPEGs.
- Missing/stale -> `--`.
- Unsupported PID -> `N/A`.
- `NO FAULT CODES` only after a valid Mode 03 read.
- Readiness is never fabricated.
