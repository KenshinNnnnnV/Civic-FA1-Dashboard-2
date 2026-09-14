# Civic FA1 Dashboard v0.3.0

Android dashboard prototype for a Honda Civic FA1 / R18A, optimized for a 1280×720 landscape head unit.

## v0.3.0 visual baseline

This version locks the new premium neon visual language for the project:

- dark night-city / graphite background
- neon green/cyan accent system
- Honda Civic FA1 hero artwork
- Street, Sport and Diagnostics modes with consistent bottom navigation
- new premium startup/splash screen
- simulator mode clearly identified as non-live data
- dynamic simulated values continue to run on top of the new visual layer
- touchable metric cards open detail overlays
- Diagnostics OBD/ECU/DTC/readiness/live-sensor cards are touchable

## Modes

### STREET
Large speed display, RPM, coolant, voltage, fuel, trip and engine load.

### SPORT
Large RPM gauge, speed, coolant, voltage, throttle, load and intake temperature.

### DIAGNOSTICS
Simulator OBD status, ECU status, metric cards, DTC area, readiness monitor area and live sensor list.

## Important

v0.3.0 still uses simulated values. It does not claim to be connected to the vehicle ECU.
The real OBD transport will be added after the exact adapter type is confirmed (Bluetooth Classic, BLE, Wi‑Fi or USB).

## Build

GitHub Actions builds a debug APK on every push to `main` and uploads the artifact as:

`Civic-FA1-Dashboard-v0.3.0-APK`
