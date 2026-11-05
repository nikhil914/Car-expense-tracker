# Head-unit / in-car refuel detection: what vehicle data an Android app can read (AAOS, Android Auto, aftermarket head units, OBD-II, OEM cloud APIs)

Scope note: the coordinator narrowed scope mid-task to **Android only**. iOS CarPlay was skipped on purpose. Hosts blocked in this session: en.wikipedia.org, python-obd.readthedocs.io, document.pleos.ai and www.csselectronics.com. OBD PID facts therefore come from search snippets and mirrors, not from the primary Wikipedia or SAE J1979 table.

## Q1. Android Automotive OS (Google built-in): which properties are readable, with which permissions, and can a third-party Play app use them?

### Takeaway
The platform API (`CarPropertyManager` + `VehiclePropertyIds`) exposes everything needed for refuel detection to non-privileged apps:
- `FUEL_LEVEL` (in **millilitres**, not %) and `RANGE_REMAINING`, `FUEL_LEVEL_LOW`, `EV_BATTERY_LEVEL`: dangerous runtime permission `CAR_ENERGY`.
- `FUEL_DOOR_OPEN`, `EV_CHARGE_PORT_OPEN`, `EV_CHARGE_PORT_CONNECTED`: normal permission `CAR_ENERGY_PORTS`.
- `INFO_FUEL_CAPACITY`: normal permission `CAR_INFO`.
- `IGNITION_STATE`, `PARKING_BRAKE_ON`: normal permission `CAR_POWERTRAIN`.
- `PERF_VEHICLE_SPEED`: dangerous permission `CAR_SPEED`.
- `PERF_ODOMETER`: dangerous `CAR_MILEAGE_3P` (the older `CAR_MILEAGE` is signature|privileged).

Three things gate real access:
- Distribution: Google Play accepts an AAOS app only if it fits a supported category.
- OEM HAL: the OEM must implement the property in its Vehicle HAL.
- OEM policy: some OEMs may block properties for third-party apps.

### Cited Findings
**Property details (quoted from the VehiclePropertyIds reference)**
- `FUEL_LEVEL` (0x11600307): "Fuel level in milliliters… will not be implemented for electric vehicles… value will not exceed INFO_FUEL_CAPACITY". READ, GLOBAL, `CHANGE_MODE_CONTINUOUS`, Float. "Dangerous permission Car.PERMISSION_ENERGY to read property." — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `FUEL_DOOR_OPEN` (0x11200308): Boolean, `CHANGE_MODE_ONCHANGE`, READ or READ_WRITE. "Normal permission Car.PERMISSION_ENERGY_PORTS or Signature|Privileged permission Car.PERMISSION_CONTROL_ENERGY_PORTS to read." Not implemented on pure EVs (use `EV_CHARGE_PORT_OPEN` there). — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `INFO_FUEL_CAPACITY` (0x11600104): "Fuel capacity of the vehicle in milliliters". `CHANGE_MODE_STATIC`, Float. "Normal permission Car.PERMISSION_CAR_INFO". — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `FUEL_LEVEL_LOW`: Boolean, ON_CHANGE, dangerous `PERMISSION_ENERGY`. Quote: "Once FUEL_LEVEL_LOW is set, it should not be cleared until more fuel is added to the vehicle". Its clearing is therefore a weak refuel hint. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `RANGE_REMAINING` (0x11600308): metres, CONTINUOUS, sum of fuel and battery range for hybrids. Read needs dangerous `PERMISSION_ENERGY`. Write needs signature `PERMISSION_ADJUST_RANGE_REMAINING`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `PERF_ODOMETER` (0x11600204): kilometres, CONTINUOUS, Float. "Dangerous permission Car.PERMISSION_MILEAGE_3P or Signature|Privileged permission Car.PERMISSION_MILEAGE to read property." — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `PERF_VEHICLE_SPEED` (0x11600207): m/s, signed (negative when reversing), CONTINUOUS, dangerous `PERMISSION_SPEED`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `IGNITION_STATE` (0x11400409): Integer `VehicleIgnitionState`, ON_CHANGE, normal `PERMISSION_POWERTRAIN`. `PARKING_BRAKE_ON` (0x11200402): Boolean, ON_CHANGE, normal `PERMISSION_POWERTRAIN`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `EV_BATTERY_LEVEL` (0x11600309): Wh, CONTINUOUS, dangerous `PERMISSION_ENERGY`. Percentage formula: `EV_BATTERY_LEVEL / EV_CURRENT_BATTERY_CAPACITY * 100`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `EV_CHARGE_PORT_OPEN` (0x1120030a): ON_CHANGE, normal `ENERGY_PORTS`. `EV_CHARGE_PORT_CONNECTED` (0x1120030b): normal `ENERGY_PORTS`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `EV_BATTERY_INSTANTANEOUS_CHARGE_RATE` (0x1160030c): mW, positive while charging, dangerous `ENERGY`. `INFO_EV_BATTERY_CAPACITY`: Wh, STATIC, normal `CAR_INFO`. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `INFO_FUEL_TYPE`: an Integer[] of FuelType values. ELECTRIC appears only for plug-ins, so a full hybrid shows {UNLEADED}. Also present: `INFO_VIN`, which needs signature|privileged `PERMISSION_IDENTIFICATION`, so third-party apps cannot read it. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- Two more fuel-economy properties need dangerous `PERMISSION_MILEAGE_3P`: `INSTANTANEOUS_FUEL_ECONOMY` (L/100km) and another property with constant 0x11600211. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)

**Permission strings and protection levels (from the `android.car.Car` reference)**

| Permission | Protection level |
|---|---|
| `android.car.permission.CAR_ENERGY` | Dangerous |
| `android.car.permission.CAR_ENERGY_PORTS` | Normal |
| `android.car.permission.CAR_MILEAGE` | Signature\|Privileged |
| `android.car.permission.CAR_MILEAGE_3P` | Dangerous |
| `android.car.permission.CAR_INFO` | Normal |
| `android.car.permission.CAR_SPEED` | Dangerous |
| `android.car.permission.CAR_POWERTRAIN` | Normal |
| `android.car.permission.CAR_IDENTIFICATION` | Signature\|Privileged |

Source: [android.car.Car](https://developer.android.com/reference/android/car/Car)

**Third-party access in practice**
- Two sources conflict:
  - An unofficial AAOS guide lists `CAR_ENERGY`/`FUEL_LEVEL` among the permissions third-party developers can use — [akshay-2 gitbook](https://akshay-2.gitbook.io/android_automotive/hardware-capabilities-in-aaos).
  - The PLEOS (Hyundai Motor Group AAOS-based platform) planning guide reportedly marks FUEL_LEVEL as **not available** to third-party developers, and says third-party apps get read-only access — [PLEOS API compatibility](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability). This comes from a search snippet; the host was blocked, so it is not verified.
  - This suggests OEMs can restrict access beyond what AOSP allows.
- Signature|privileged permissions need the platform certificate or a privileged system install, so Play apps cannot use them — [Medium: AAOS permissions](https://medium.com/@sreelakshmis.dilip/android-automotive-understanding-app-permissions-and-privileged-applications-c00b83df11d6).

**Google Play distribution rules**
- Supported categories, per the car app quality guidelines:

| Category | Android Auto | AAOS |
|---|---|---|
| Media | yes | yes |
| Communication (messaging, calling) | yes | no |
| Navigation | yes | yes |
| POI | yes | yes |
| IoT | yes | yes |
| Weather | yes | yes |
| Video | no | yes |
| Games | yes | yes |
| Browsers (labs) | no | yes |

  — [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- Games went to general availability in September 2026 — [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- Quote: "Your app must belong to one of the supported categories and meet specific design requirements before it can be listed on Google Play for Android Auto and Android Automotive OS." — [Android for Cars App Library](https://developer.android.com/training/cars/apps)
- PE-1 (car optimized; Media, Navigation, POI, IoT, Weather): on AAOS, "with the exception of providing setup, settings, and sign-in flows while parked, the app must not provide any functionality through its own activities". The driving UI must be templated. — [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- Tier 3 "Car ready": through the Car ready mobile apps program, large-screen-compatible mobile apps can be downloaded on AAOS cars. This tier "primarily applies to the parked categories". DD-3: parked apps must not be launchable or usable while driving. — [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- `CarInfo.fetchExteriorDimensions` and the Model and EnergyProfile data need `android.car.permission.CAR_INFO` on AAOS — [CarInfo](https://developer.android.com/reference/androidx/car/app/hardware/info/CarInfo)

### Inferences
- **Fuel volume is direct.** On AAOS, `FUEL_LEVEL` is already in mL. Litres added = (FUEL_LEVEL_after − FUEL_LEVEL_before) / 1000. No tank-capacity multiplication is needed. `INFO_FUEL_CAPACITY` serves as a sanity bound and gives the percentage.
- **Best AAOS event signal.** Use the edges of `FUEL_DOOR_OPEN` (ON_CHANGE, normal permission, no user prompt), bracketed by `FUEL_LEVEL` samples. Confirm that `PERF_VEHICLE_SPEED == 0`, `GEAR_SELECTION == PARK` or `PARKING_BRAKE_ON` holds, and watch the `IGNITION_STATE` transitions.
- **Background caveat.** A pure "fuel log" app is not a supported category. The realistic paths are:
  - A **POI app (fuel stations)**, which is explicitly a category: "point-of-interest, such as parking and charging". Fuel-station finders fit POI.
  - Or an OEM or partner pre-install.
  - Detection must run in a background service or listener. Whether a Play-installed POI app may keep a `CarPropertyManager` callback alive while the app is not foregrounded is not documented. Treat this as a risk.
- **Odometer limit.** Mileage via Car App Library `CarInfo` is explicitly "not available to AAOS apps installed from Google Play" (see Q2). Reading `PERF_ODOMETER` directly through `CarPropertyManager` with `CAR_MILEAGE_3P` appears to be the newer sanctioned path.

### Gaps
- No source found on the API level that introduced `CAR_MILEAGE_3P`. It is likely recent; confirm in the API diff.
- No source found on which OEMs (Volvo, Polestar, GM, Renault, Honda) implement `FUEL_DOOR_OPEN` or `FUEL_LEVEL` in the VHAL, or whether they whitelist them for third parties.
- The PLEOS page could not be fetched (host blocked).
- No source found on whether Play review accepts a POI app that also auto-logs refuels.

## Q2. Android Auto (phone projection) with the Jetpack Car App Library: what does CarInfo give, with which permissions, and how reliable is it?

### Takeaway
On Android Auto, `CarHardwareManager.getCarInfo()` gives:
- `EnergyLevel`: fuelPercent, batteryPercent, rangeRemainingMeters, energyIsLow.
- `Mileage`: odometer.
- `Speed`, plus `EnergyProfile`, `Model`, `TollCard` and an experimental `EvStatus`.

It needs Car App API level 3+ and the GMS permissions `CAR_FUEL`, `CAR_MILEAGE` and `CAR_SPEED`. It works only while the app's templated session is running in a supported category (POI suits fuel). Data comes only as a percentage: litres = Δ% × user-entered tank capacity. There is no fuel-door signal. Many cars return non-success statuses.

### Cited Findings
- `CarInfo` is annotated `@RequiresCarApi(value = 3)`, artifact `androidx.car.app:app`, added in 1.1.0.
- Methods:
  - `addEnergyLevelListener` and `addMileageListener`
  - `addSpeedListener` and `addTollListener`
  - `fetchEnergyProfile` and `fetchModel`
  - `fetchExteriorDimensions` (`@RequiresCarApi(7)`; "only… meaningful data on select Automotive vehicles running SDK 30 and above")
  - `addEvStatusListener` (`@ExperimentalCarApi`, 1.2.0)

  — [CarInfo](https://developer.android.com/reference/androidx/car/app/hardware/info/CarInfo)
- `EnergyLevel` fields, each wrapped in `CarValue<T>`:
  - `getFuelPercent()`, Float
  - `getBatteryPercent()`, Float
  - `getRangeRemainingMeters()`, Float
  - `getEnergyIsLow()`, Boolean
  - `getDistanceDisplayUnit()`, Integer
  - `getFuelVolumeDisplayUnit()`, Integer (experimental)

  — [EnergyLevel](https://developer.android.com/reference/androidx/car/app/hardware/info/EnergyLevel)
- Permission table from the Car Hardware APIs page (page last updated 2026-09-08):

| Methods | Data | Android Auto permission | AAOS permission | Min Car App API |
|---|---|---|---|---|
| `fetchModel` | make, model, year | (none) | `android.car.permission.CAR_INFO` | 3 |
| `fetchEnergyProfile` | EV connector types, fuel types | `com.google.android.gms.permission.CAR_FUEL` | `CAR_INFO` | 3 |
| `addEnergyLevelListener` | battery level, fuel level, fuel level low, range | `CAR_FUEL` | `CAR_ENERGY` + `CAR_ENERGY_PORTS` + `READ_CAR_DISPLAY_UNITS` | 3 |
| `addSpeedListener` | raw speed, display speed | `com.google.android.gms.permission.CAR_SPEED` | `CAR_SPEED` + `READ_CAR_DISPLAY_UNITS` | 3 |
| `addMileageListener` | odometer | `com.google.android.gms.permission.CAR_MILEAGE` | "Data not available to Android Automotive OS apps installed from Google Play." | 3 |
| Toll listeners | toll card state | — | — | 3 |

  — [Car Hardware APIs](https://developer.android.com/training/cars/apps/library/car-hardware-api)
- Setup requirements:
  - Android Auto needs the `androidx.car.app:app-projected` dependency. AAOS needs `androidx.car.app:app-automotive`.
  - Permissions must be declared in the manifest and granted by the user.
  - "Don't assume that the car's data is available at all times… check the status".
  - On AAOS, `CarSensors` returns `STATUS_UNIMPLEMENTED`.

  — [Car Hardware APIs](https://developer.android.com/training/cars/apps/library/car-hardware-api)
- Community answer on real-world support: "not all vehicles support all of the fields… use data to augment your app, but not rely on it being available" — [Stack Overflow mirror](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/76950855/android-auto-getting-data-about-car)
- Car App Library categories for templated apps: navigation, POI, IoT and weather — [Android for Cars App Library](https://developer.android.com/training/cars/apps)

### Inferences
- **Detection method.** No fuel-door event exists. Detect refuels from a jump in `fuelPercent` between consecutive listener callbacks, or between the last value of one projection session and the first value of the next. The phone must store the last value across sessions.
- **Session limit.** Listeners run only while the car app's `Session` is alive, meaning projected and connected. Refuels with the engine off and the phone disconnected are caught only on the next connection, as a step versus the last stored value.
- **App type.** A POI "fuel stations near me" app is the natural shell that justifies the category and the `CAR_FUEL` permission.
- **Granularity.** The precision of `fuelPercent` is OEM-dependent. Some cars may report coarse steps, such as gauge bars.

### Gaps
- No public list of vehicle models that populate `fuelPercent` or `Mileage` on Android Auto.
- No data on the update rate of energy-level callbacks.

## Q3. Aftermarket Chinese Android head units with CAN decoder boxes (Joying, ATOTO, Teyes, Dasaita, FYT, MTCD): do they expose fuel or odometer data to third-party apps?

### Takeaway
There is **no documented, stable public API.**
- **How data flows.** Head units do not read the CAN bus directly. A vehicle-specific decoder box (Raise/RZC, Hiworld, Simple Soft, LZ, and others) translates CAN frames to a UART protocol. A vendor system app parses that stream, for example `com.syu.canbus` on FYT units (Joying, some ATOTO and Teyes variants).
- **How apps get it.** Third parties get data only by reverse-engineering the vendor app's internal codes. The FYT Launcher Mod does this through `DataCanbus.DATA[...]`.
- **What is exposed.** Coverage varies per car and per firmware. Exposed fields are usually fuel consumption and range, not necessarily fuel level or odometer.
- **Practical answer.** An OBD dongle is more portable.

### Cited Findings
- A Teyes owner thread says Teyes units don't read the CAN bus directly, and each ships with a CAN decoder box. The vehicle choice in settings (such as "LZ > Toyota > Lexus") selects the decoder maker's protocol. Quote: "there's not… a logical mapping between the data sent from the Teyes to the CANbus decoder, and then from the decoder to the vehicle". — [ih8mud forum](https://forum.ih8mud.com/threads/looking-for-a-picture-of-the-lx470-climate-control-screen.1305009/post-14826155)
- FYT Launcher Mod, the most concrete third-party example:
  - Reads values from FYT's `com.syu.canbus` via the `DataCanbus` class using numeric codes. Examples: `DataCanbus.DATA[102]` = fuel consumption, `[103]` = range.
  - Subscribes via `DataCanbus.NOTIFY_EVENTS`.
  - Some cars need a request command, sent via `DataCanbus.PROXY.cmd(...)`.
  - Codes are found by decompiling `com.syu.canbus` or with the mod's built-in CAN reader.
  - Live stats need the Accessibility Service permission.

  — [FYT Launcher Mod (GitHub)](https://github.com/vasyl91/FYT-Launcher-Mod)
- Limits listed by the same project:
  - Codes vary by car and app version; a wrong code reads 0.
  - Some units don't provide the data at all.
  - Consumption was unavailable below about 30–40 km/h on a tested car.
  - Fuel level and odometer are not covered by the widget.
  - CAN features stay off on custom ROMs signed with the AOSP test key.

  — [FYT Launcher Mod (GitHub)](https://github.com/vasyl91/FYT-Launcher-Mod)
- Android 8+ no longer delivers some FYT system broadcasts (such as ACC-on) to non-system apps — [FytHWOneKey (GitHub)](https://www.github.com/hvdwolf/FytHWOneKey). Related: FET (FYT Extra Tools) — [hvdwolf/FET](https://github.com/hvdwolf/FET)
- OBD M8 works with ELM327 adapters, including the Teyes OBD2 adapters v1 and v2, and shows distance-to-empty. This is an OBD path, separate from the canbox. — [zaa.bz/obdm8](https://zaa.bz/obdm8)
- No results on MTCD/Hal9k (`com.microntek`) CAN broadcast intents for fuel data. The search returned nothing relevant.

### Inferences
- **Feasible but fragile.** Support must be per firmware family (FYT `com.syu.canbus` codes, Teyes, MTCD) and per car profile. Expect zero coverage for many UAE-market cars whose canbox does not decode fuel level.
- **Product recommendation.** Treat this as "optional experimental integration". Ship OBD-II plus manual entry as the baseline on aftermarket head units.
- **Running on the head unit.** The app can run directly on the head unit as a normal Android app. The unit is a full Android tablet with no AAOS category restrictions. Sideloading or Play installs work. It can talk to a BT or Wi-Fi OBD dongle itself.

### Gaps
- No documented Teyes SDK or public intent list.
- Raise, Hiworld and Simple Soft UART protocols are not publicly documented in the sources found.
- It is unknown whether `com.syu.canbus` exposes a raw fuel-level code for common UAE models (Toyota Land Cruiser, Patrol, Camry, Hilux).

## Q4. OBD-II Bluetooth/Wi-Fi dongle fallback (ELM327, OBDLink): which PIDs exist, can an app compute fuel added and consumption, and which libraries help?

### Takeaway
The useful Mode 01 PIDs are:
- `0x2F` fuel tank level input: A×100/255 %.
- `0x0D` speed: km/h.
- `0x5E` engine fuel rate: (256A+B)/20 L/h.
- `0xA6` odometer: 4 bytes, 0.1 km resolution.
- `0x10` MAF, for fuel flow when `0x5E` is missing.

Support varies by make. `0x2F` and `0xA6` are often absent; VW and Ford examples follow below. Fuel level is a smoothed gauge-sender value with slosh noise. An app can compute litres added = Δ% × tank capacity, and consumption = ∫fuel rate dt, or MAF-derived.

### Cited Findings
- PID 2F "Fuel Tank Level Input" is scaled 100/255 to give 0–100 % — [Wikipedia OBD-II PIDs (via search snippet; host blocked)](https://en.wikipedia.org/wiki/OBD-II_PIDs); [Scribd J1979 table](https://www.scribd.com/document/399698761/OBD-II-PIDs-pdf)
- Real cars often don't answer:
  - A 2018 VW Passat did not respond to the fuel-level PID. It did not answer A6 either, although A6 was in its supported-PID bitmap. — [AutoPi community](https://community.autopi.io/t/vw-passat-2018-read-out-fuel-level-and-odometer-value-not-possible/3286)
  - An F-150 owner got no response on 2F — [ford-trucks.com](https://www.ford-trucks.com/forums/1674086-fuel-level-pid.html)
- Fuel-level signals are noisy from slosh and are accurate mainly when stopped on level ground — [MoTeC forum](https://forum.motec.com.au/viewtopic.php?p=14460)
- On some GM trucks, the fuel-level % shown is not computed directly from the sender — [tahoeyukonforum](https://tahoeyukonforum.com/threads/wacky-fuel-gauge.55319/page-2)
- PID 5E engine fuel rate is (256A+B)/20 L/h. Sources give the range as 0–3212.75 or 0–3276.75 L/h. A firmware header decodes it as `((a*256.0f)+b)/20.0f`. — [OBD2 PIDs header](https://git.supported.systems/warehack.ing/i-k-bus-board/src/branch/main/firmware/lib/KLine/Obd2Pids.h); [IPFS Wikipedia mirror](https://bafybeiemxf5abjwjbikoz4mc3a3dla6ual3jsgpdr4cjr3oz3evfyavhwq.ipfs.4everland.io/wiki/OBD-II_PIDs.html)
- PID A6 odometer is 4 bytes, maximum 429,496,729.5 km, which implies (A·2²⁴+B·2¹⁶+C·2⁸+D)/10 km. The formula is inferred from the max value. — [Scribd OBD doc](https://www.scribd.com/document/809757342/OBD)
- MAF fuel flow: Fuel (L/h) = MAF(g/s)×3600 / (AFR × density g/L), with AFR 14.7 for gasoline.
  - One paper uses a density of 820 g/L — [arXiv 1701.02160](https://arxiv.org/pdf/1701.02160). Another uses 0.74 g/mL for gasoline and 0.85 g/mL for diesel — [koreascience](https://koreascience.kr/article/CFKO201028451824104.pdf).
  - Enrichment at warm-up and high load breaks the 14.7 assumption — [ecomodder](https://ecomodder.com/forum/192639-post5.html)
  - The OBD standard defines no fuel-consumption parameter. Speed is mandatory, fuel rate is not. — [arXiv 1611.09065 DrivingStyles](https://arxiv.org/pdf/1611.09065)
- Android libraries:
  - pires `obd-java-api` (1.0-RC16; Maven artifact) — [obd-java-api fork](https://github.com/designsome/obd-java-api)
  - `Tomiwa-Ot/obd`, Java, ELM327 over USB or Bluetooth, `Mode01` enum — [Tomiwa-Ot/obd](https://github.com/Tomiwa-Ot/obd)
  - `barnhill/androidobd`, Kotlin — [barnhill/androidobd](https://awesome.ecosyste.ms/projects/github.com%2Fbarnhill%2Fandroidobd)
- Existing apps: OBD Fusion advertises MPG calculation and CSV logging. Torque Pro offers logging and fuel-economy plugins; one user reports its fuel-economy figures were far off. No app was found that advertises *automatic refuel detection*. — [OBD Fusion App Store](https://apps.apple.com/gb/app/obd-fusion/id650684932?uo=2); [toyotaownersclub](https://www.toyotaownersclub.com/forums/topic/141470-torque-on-android-sensor-readings/page/2/)

### Inferences
- **UAE compatibility.** The fleet is mostly post-2008 Japanese, Korean, American and German petrol cars. EOBD/OBD-II via ISO 15765 CAN should connect. But 2F and A6 availability must be probed per car with the PID-support bitmaps (`0100`, `0120`, …, `01A0`). Toyota and Nissan often need manufacturer-specific Mode 22 PIDs for fuel level and odometer, as Torque custom-PID communities show. That last point is not verified in these sources.
- **Dongle choice.** ELM327 clones are cheap but often slow or buggy. OBDLink (STN chips) is more reliable. For BLE dongles, the app must handle both BLE GATT and classic SPP RFCOMM.

### Gaps
- Could not fetch the primary PID table (Wikipedia, CSS Electronics and readthedocs were blocked).
- No UAE-specific compatibility data found.
- No source found for Mode 22 fuel-level PIDs per make.

## Q5. Refuel-detection algorithm, liters estimate, accuracy limits, and price

### Takeaway
Combine an event trigger with stable before and after levels:
- Triggers: fuel-door edge (AAOS only), an ignition-off-to-on cycle, or a long stationary period.
- Levels: median-filtered readings, taken stationary and on level ground.

Accept a refuel if Δ ≥ max(5 L, 8 % of tank) and odometer Δ < 1 km. The volume comes from:
- AAOS: Δ mL directly.
- Android Auto and OBD: Δ% × tank capacity.

Expect an error of about ±5–10 % of tank capacity. Gauge sender smoothing, non-linear tank geometry and slosh drive this. Treat the value as an estimate and let the user correct it. Price comes from the UAE monthly fuel price × litres, or from a receipt. The vehicle has no price data.

### Cited Findings
- `FUEL_LEVEL_LOW` "should not be cleared until more fuel is added". Its clearing is a corroborating signal. — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- `FUEL_DOOR_OPEN` is ON_CHANGE (event-driven) and `FUEL_LEVEL` is CONTINUOUS (sampled) — [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- Fuel-level readings suffer from slosh and are reliable mainly when stopped and level — [MoTeC forum](https://forum.motec.com.au/viewtopic.php?p=14460)
- The FYT canbus app stops reporting consumption at low speed, so canbox data has gaps — [FYT Launcher Mod](https://github.com/vasyl91/FYT-Launcher-Mod)

### Inferences (proposed algorithm, not taken from a source)
1. **Prepare.** Persist the last stable reading `L_prev`, `odo_prev` and its timestamp. A reading is stable when speed = 0, gear = P or the parking brake is on, and the median of N samples over at least 10 s is used.
2. **Triggers:**
   - (a) AAOS `FUEL_DOOR_OPEN` true then false.
   - (b) An `IGNITION_STATE` cycle OFF/ACC then ON, or an AA or OBD session reconnect.
   - (c) Speed = 0 for more than 2 min near a fuel-station geofence. Use the POI data from the phone's location.
3. **After the trigger,** wait for a stable `L_new`. Allow 30–120 s after ignition on, because many clusters re-sample the sender at startup.
4. **Accept** if both hold:
   - ΔL = L_new − L_prev ≥ max(5 L, 0.08 × capacity)
   - |odo_new − odo_prev| < 1–2 km
5. **Compute litres:**
   - AAOS: ΔmL / 1000.
   - Android Auto and OBD: Δ% / 100 × capacity. Take capacity from `INFO_FUEL_CAPACITY`, the user, or a VIN/model lookup.
   - Clamp the result to capacity − L_prev.
6. **Full-tank flag.** Mark "full tank" if L_new ≥ 97 % of capacity. This allows accurate full-to-full consumption math between fills, independent of gauge error.
7. **Refine.** After 20–50 km of driving, re-estimate L_new from the filtered trend. The gauge may lag or round. Optionally reconcile with integrated fuel-rate consumption (PID 5E or MAF).
8. **Price.** Litres × that month's UAE retail price for the grade (Super 98, Special 95, E-Plus 91, diesel). The user picks the grade once. Alternatively, import the actual amount from an ADNOC or ENOC digital receipt or the payment SMS.

**Accuracy caveats:**
- Many senders are float or resistive types with deliberately damped output.
- Tank shapes are non-linear: saddle tanks in 4x4s, and the top is often "flat" on the gauge for several litres.
- A low percentage resolution of about 1 % (0.4 % for OBD byte scaling) on a 70–93 L tank equals about 0.7–0.9 L per step. Real error is dominated by sender damping, not quantization.

### Gaps
- No primary source found quantifying OEM fuel-gauge error. The figure of about ±5–10 % is an engineering estimate and needs validation in field tests.
- UAE monthly fuel price data and ADNOC/ENOC receipt APIs were not researched here; they are out of this note's scope.

## Q6. Manufacturer connected-car cloud APIs (Smartcar, Tesla, Mercedes, BMW, High Mobility, Volvo): fuel and odometer data remotely, and UAE availability

### Takeaway
Cloud APIs do provide fuel level and odometer. But the public coverage found lists **North America and Europe only** for Smartcar, BMW and Mercedes (the latter two via High Mobility). Tesla Fleet API routes the Middle East to its EU host. Nothing confirms UAE availability for any provider except Tesla's region routing.

### Cited Findings
- Smartcar fuel tank API: amount remaining (L or gal), percentage and range. Coverage pages say "North America and Europe". The brand count differs between pages (36 vs 39 brands). No UAE mention found. — [Smartcar fuel](https://smartcar.com/product/fuel); [Smartcar fuel tank API blog](https://smartcar.com:443/blog/fuel-tank-api); [get-fuel-tank docs](https://smartcar.com/docs/api-reference/get-fuel-tank.md)
- Tesla Fleet API base URL for "Europe, Middle East, Africa" is `https://fleet-api.prd.eu.vn.cloud.tesla.com`. Vehicle data needs the `vehicle_device_data` scope. — [Tesla base URLs](https://developer.tesla.com/docs/fleet-api/getting-started/base-urls); [Tesla regions](https://developer.tesla.com/docs/fleet-api/getting-started/regions-countries); [tesla_fleet_api Dart](https://pub.dev/documentation/tesla_fleet_api/latest/)
- High Mobility details:
  - BMW/MINI third-party access is offered in a European country list, with Mileage and Fuel data packages — [High Mobility BMW & MINI](https://docs.high-mobility.com/oem-guides/bmw-and-mini)
  - Mercedes requires Mercedes me connect with the "Interface to Third Parties" service, and fuel level is limited to 1 request per car per hour — [High Mobility Daimler](https://docs.high-mobility.com/guides/platform/daimler)
  - Hyundai has been available since July 2026, with odometer and fuel level — [High Mobility Hyundai](https://www.high-mobility.com/blog/hyundai-now-available-on-high-mobility)
- BMW CarData community notes:
  - Petrol cars send tank level, range and odometer, but some MINI petrol models send no tank level — [BavarianData wiki](https://github.com/JustChr/BavarianData/wiki/Supported-Cars)
  - A limit of about 50 requests per vehicle per day is reported by a third party and is unverified — [Loxone](https://www.loxone.com/huhu/?p=53796)
- Mercedes-Benz Fleet Partner API offers data packages (Fuel Level, Odometer, SoC). It is "not available in markets where Mercedes-Benz vehicles do not have a communication module as standard". — [Mercedes-Benz connectivity](https://connectivity.mercedes-benz.com/products/mercedes-benz-fleet-partner-api)
- Volvo Extended Vehicle API exists — [Volvo developer](https://developer.volvocars.com/apis/extended-vehicle/v1/overview)

### Inferences
- **Detection logic.** Cloud polling can detect refuels with the same Δ-level logic, sampled hourly, with no in-car app needed.
- **UAE caveat.** Coverage is doubtful for GCC-spec cars, which often lack EU telematics enrolment. Tesla is the most plausible UAE-ready option.

### Gaps
- No UAE or GCC country lists confirmed for Smartcar, High Mobility, BMW, Mercedes or Toyota/Lexus (Toyota not researched for lack of tool budget).
- The Tesla regions page snippet was truncated, so it is unclear whether the UAE is listed as a supported country.
