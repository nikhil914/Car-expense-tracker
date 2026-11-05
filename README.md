# Sayyara — UAE car expense & fuel tracker

An Android-first fuel and vehicle expense tracker for the UAE, built as a better Fuelio:
- Correct fuel economy for partial and missed fill-ups.
- Exact official monthly UAE fuel prices.
- Salik, Darb and parking costs.
- Mulkiya reminders.
- Fuelio import.
- Arabic and English.
- Refuel detection from the car's head unit.

## Modules
| Module | What | Builds here? |
|---|---|---|
| `core/` | Kotlin Multiplatform domain + engines: consumption, stats, Fuelio import/CSV export, receipt/odometer OCR parsing, UAE fuel prices, Salik/Darb tolls, parking, route cost, refuel/charge detection, OBD-II parsing, reminders | Yes, JVM target (`gradle :core:jvmTest`, 133 tests) |
| `app/` | Android phone app: Compose M3 UI, Room, WorkManager, Glance widget, Quick Settings tile, Android Auto POI app with `CarInfo` refuel detection | Needs Android SDK |
| `automotive/` | Android Automotive OS head-unit app: `CarPropertyManager` refuel detection, see `automotive/README.md` | Needs Android SDK |

Android modules are included only when `ANDROID_HOME` or `local.properties` `sdk.dir` is set (`settings.gradle.kts`).

## Build
```bash
gradle :core:jvmTest          # core tests, plain JDK 21
gradle :app:assembleDebug     # phone app (Android SDK 36 required)
gradle :automotive:assembleDebug
```

## Docs
- `docs/BUILD_BRIEF.md`: architecture, module ownership, core API contract.
- `docs/research/roadmap-report.md`: research report and product roadmap.
- `docs/research/notes/`: raw research notes.

## Status / caveats
- **The Android code has not been compiled yet.** It was written against current AndroidX APIs without access to the Android SDK, so expect a round of compile fixes on the first SDK build.
- **UAE data needs checking before release.** Verify the fuel prices, toll gate coordinates and tariffs in `core/.../uae` against the official sources: WAM, RTA, AD Mobility.
- **The price feed URL is a placeholder.** `PRICE_FEED_URL` needs a real backend.
