# v0.8.1 audit — final visual lock

## Why another visual revision was needed
The earlier installed versions were rejected because their programmatic reconstruction changed proportions, panel borders, tachometer scale, spacing and overall density. The user explicitly required the final approved screens and no alternative design.

## v0.8.1 response
- The latest approved disconnected CONNECT / SPORT / DIAGNOSTICS images are now the locked 1280×720 runtime artwork.
- SPORT and DIAGNOSTICS are left untouched in the disconnected state except for the real clock, so the no-OBD visual test is as close as possible to the approved pixels.
- CONNECT remains functional: example device/session content is masked and replaced with real discovery/selection state, preventing baked fake devices from being presented as real.
- When connected, only value/status regions change. Card geometry, artwork, navigation and tachometer shell remain fixed.

## OBD boundary
This version does not claim to fix the UIS8581A Bluetooth issue. Multiple OBD adapters and Car Scanner on the head unit fail to establish the data connection, so Bluetooth/head-unit diagnosis remains a separate workstream.
