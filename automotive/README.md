# Sayyara for in-car head units

Ways to learn that the car was refuelled:

| Route | Data | Status in this repo |
|---|---|---|
| **Android Automotive OS** (Google built-in: Volvo, Polestar, GM, Renault, Honda…) | `FUEL_LEVEL` (mL), `FUEL_DOOR_OPEN`, `IGNITION_STATE`, `INFO_FUEL_CAPACITY`, `PERF_ODOMETER` (`CAR_MILEAGE_3P`), `EV_BATTERY_LEVEL` via `CarPropertyManager` | This module (`:automotive`). POI car app, `AaosRefuelMonitor`, pending-refuel store and confirm screens |
| **Android Auto** (phone projection) | `CarInfo` energy level (fuel %), mileage, speed. No fuel door | `app/.../car/` (`CarRefuelMonitor`, POI app) |
| **Aftermarket Android units** (FYT/Joying/Teyes + CAN box) | Vendor system app (`com.syu.canbus`), reverse-engineered codes only | Not supported |
| **OBD-II dongle** (ELM327 / OBDLink) | PID `0x2F` fuel %, `0xA6` odometer, `0x0D` speed, `0x5E`/MAF fuel flow | Parser in `core/.../obd`, experimental Bluetooth client in `app/.../car/obd` |

The car never knows the price. Litres added × the official UAE price for the month gives the cost (`FuelPriceBook`).

## Limits
- Google Play only accepts AAOS apps in supported categories, and this app ships as a **POI** app. Auto-logging is a secondary feature; check Play policy before release.
- Carmakers can block properties even when Android allows them. Every property is probed with `AaosCarReader.isAvailable`.
- No foreground service runs. Detection happens while the session is alive, plus periodic `RefuelSnapshotJobService` snapshots that compare levels across ignition cycles.
- Detected refuels are stored on the head unit (`PendingRefuelStore`). They sync to the phone app in Phase 2, through the cloud backend.
- Expect ±5–10% of tank capacity error from the fuel gauge (engineering estimate).

## Build
Needs the Android SDK with the `android.car` library: `useLibrary("android.car")`.
