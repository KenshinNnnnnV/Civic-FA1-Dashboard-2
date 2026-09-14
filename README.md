# Civic FA1 Dashboard v0.3.3

Test/simulator build for 1280x720 Android head unit.

## v0.3.3
- Keeps the existing visual design.
- Sport tachometer illumination is now driven by the live RPM value.
- Upper sequential shift-light bar reacts to RPM.
- Shift-light colors progress green -> yellow -> red.
- Simulator throttle/load demand is coupled to requested RPM so acceleration looks coherent.
- All numeric values remain single live UI values; no duplicate value layer is added.

Current OBD status remains simulator/test data until a real adapter transport is implemented.
