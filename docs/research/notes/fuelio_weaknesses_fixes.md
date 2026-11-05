# Fuelio (Sygic) weaknesses and how a competing app should fix them

Research date: 2026-11-05. Network note: direct fetches of fuel.io, fuelio.userecho.com, apptail.io, brimlyauto.com failed (DNS blocked in sandbox); apps.apple.com / play.google.com not attempted per instructions. Evidence below comes from search-result snippets of those pages plus reachable pages (GitHub, LubeLogger docs, Simply Auto docs, ML Kit docs). Snippet-level evidence is flagged where relevant. No Reddit (r/cars, r/dubai) or Trustpilot threads about Fuelio surfaced in searches.

## 1. iOS feature parity gap (costs, trip log, reminders)

### Takeaway
App Store reviews from 2022 to Jan 2026 repeatedly say the iOS app lacks Android features (costs/maintenance, trip log, reminders, trip-cost calc, editing entries). Fuelio has since added Trip Log to iOS (with CarPlay, Live Activities), but parity complaints and a 3.0-star iOS written-review average persist.

### Cited Findings
- Jan 2026 iOS (Canada) reviewer: app "doesn't even have a trip log" for business trips; switching apps — [apptail.io Canada review aggregate (search snippet)](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/canada)
- 2025 Czech iOS review cites a missing costs module; other reviews: "isn't possible to track the expense for maintenance only for fuel"; missing reminders; Bulgarian reviewer says Android calculates trip cost but iOS does not; reviewers report inability to edit an entry once created, and photos not importing from another device — [apptail.io Czechia](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/czechia), [apptail.io Bulgaria](https://apptail.io/app/fuelio-dziennik-tankowan-YAt/bulgaria) (snippets)
- Trip Log originated on Android in v7.4 as the "top requested suggestion", with Bluetooth-pairing auto-start — [fuel.io Trip Log news](https://fuel.io/news_trip_log.html), [Sociable](https://www.sociable.co/technology/fuelio-introduces-trip-log-allowing-drivers-to-record-their-daily-commuting-expenses/amp)
- Current iOS listing: "Trip Log also works with CarPlay, Live Activities, and iCloud backup" — [App Store listing (snippet)](https://apps.apple.com/us/app/fuelio-gas-log-mpg-tracker/id1487753318)
- Developer on UserEcho: some trip-recording failures come from manufacturer limits and cannot be fully fixed; advises disabling battery saver, GPS in accuracy mode — [UserEcho dev comments (snippet)](https://fuelio.userecho.com/users/1-adrian/comments/?page=5)
- Aggregator: iOS written-review average 3.0 stars / 553 reviews; some users say iOS differs from Android — [mwm.ai](https://mwm.ai/apps/fuelio-gas-log-mileage/1487753318)
- The iOS app (id1487753318) is a separate codebase released ~2019-2020, years after the Android com.kajda.fuelio app — inferred from store IDs [App Store](https://apps.apple.com/us/app/fuelio-mpg-mileage-tracker/id1487753318), [Google Play](https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en_US)

### Inferences
- Root cause is likely two native codebases with Android first. Fix: one shared domain core (Kotlin Multiplatform shared module, or Flutter/React Native) holding the calculation engine, data model, sync, and import/export; only UI and OS integrations (widgets, App Intents, Live Activities, Android Tiles/shortcuts) native. Ship features to both platforms on the same release train; publish a public parity matrix.
- Shared core also guarantees identical consumption numbers on both platforms (avoids "same data, different average" complaints after cross-platform migration).
- Background GPS trip logging is fragile on OEM Android (aggressive battery killers). Offer: Bluetooth/CarPlay/Android Auto connect auto-start, foreground service with notification, onboarding page linking to dontkillmyapp-style per-OEM steps, and a fallback "odometer-only" trip mode needing no GPS.
- Editing any record (including photos) must be supported on day one; reviewers treat no-edit as a dealbreaker.

### Gaps
- No authoritative, current (2026) Fuelio iOS vs Android feature matrix found; Fuelio's site was unreachable. Some gaps (trip log) are closed now; costs/reminders status on iOS 2026 unconfirmed.

## 2. Wrong average consumption with partial / missed fill-ups — correct algorithms

### Takeaway
Fuelio uses a "full tank algorithm"; partial or missed fills produce no figure or distorted figures, and the same problem appears in Fuelly. The industry-standard fix is full-to-full with partial accumulation and explicit missed-fill chain breaks, optionally improved by tank-level (gauge) estimation.

### Cited Findings
- Fuelio FAQ: "Fuelio is using full tank algorithm to calculate consumption" — [fuel.io FAQ (snippet)](https://fuel.io/faq_fuel_consumption.html)
- Fuelly user: after a partial, Fuelly "calculated and displayed my new average mpg as if it had been a full fuel-up, greatly (and inaccurately) increasing the displayed mpg"; staff: partials still contribute to overall average — [Fuelly forum "Partial Fuel-Up distorts MPG"](https://www.fuelly.com/forums/f2/partial-fuel-up-distorts-mpg-17383-post182625.html); wrong value persists "until that next complete fuel-up" — [Fuelly forum partial fill problem](https://www.fuelly.com/forums/f2/partial-fill-up-problem-16471-post177372.html)
- Fuelly: on next full fill, add distance and fuel of preceding partial(s) to current fill — [Fuelly forum](https://www.fuelly.com/forums/f2/when-using-partial-fill-ups-18626.html)
- Simply Auto: partial usable if full tank before and after; after a missed fill-up "restarts the fuel efficiency calculation from the fill-up after the missed one" — [Simply Auto user guide](https://simplyauto.app/userguide/fill-ups.html)
- LubeLogger: unchecking "Is Filled To Full" defers calc to next full fill; missed fill resets the calculation (shows "---") — [LubeLogger wiki](https://docs.lubelogger.com/Records/Fuel%20Records/)
- Drivvo: mark next fill "Missed previous refueling" to restart chain — [Drivvo](https://www.drivvo.com/en-US/fuel-consumption/)
- Open-source spec (drivometry): full-to-full computed at the closing full tank; partial distance+litres queued until next full; gauge-based fallback from tank capacity and gauge % change; chain breaks on missed fills, odometer resets, non-increasing odometer; recompute in batch after any add/edit/delete; never alter earlier history — [GitHub issue](https://github.com/ariful06/drivometry/issues/1), [PR](https://github.com/ariful06/drivometry/pull/2)
- Fuelio dev: user can set tank capacity and estimate litres used while adding a partial; iOS fix "Showing % when setting tank level" — [UserEcho (snippet)](https://fuelio.userecho.com/users/1-adrian/comments/?page=5), [App Store release notes (snippet)](https://apps.apple.com/gt/app/fuelio-fuel-log-mileage/id1487753318?l=en-GB)

### Precise math (author's derivation from the above methods; standard algebra, not from a single source)
Notation per fill i: odometer O_i, litres pumped V_i, flags full_i (filled to brim), missed_i (user says a fill before this one was not logged), optional gauge level before fill g_i^pre and after g_i^post as fractions of capacity C.

A. Full-to-full (baseline). Let a and b be consecutive full fills with no chain break between them. Fuel burned between them equals all fuel added after a up to and including b:
- Distance D = O_b − O_a
- Fuel F = Σ_{i=a+1..b} V_i (all partials between plus V_b; V_a excluded)
- Consumption L/100 km = 100·F / D ; km/L = D / F ; MPG(US) = (D_mi)/(F_gal)
- Assign the resulting value to the whole segment (a, b]; partial fills inside display "pending" until b exists, never a per-fill number. This is "partial accumulation".

B. Missed fill handling. If any fill k in (a, b] has missed_k = true, fuel added during the gap is unknown, so F is understated and consumption looks too good. Rule: break chain; discard segment; the next valid segment starts at the first full fill at or after k. Distance driven in the broken segment still counts for cost-per-km and total distance stats, but not for consumption.

C. Lifetime / period average — weight by distance, never average the per-tank numbers:
- Avg L/100 km = 100 · Σ_s F_s / Σ_s D_s over valid segments s only.
- Wrong: mean of per-segment L/100 km (unweighted) — biases toward short tanks. Wrong: total litres ÷ total distance across a missed gap or including the very first fill's litres (first fill's fuel was burned before tracking started; exclude V_first).
- For a period (month) where segments straddle boundaries, prorate a segment's fuel by distance fraction inside the period.

D. Tank-level-based estimation (lets every fill, partial or not, produce a number). With gauge reading before fill g_i^pre (fraction) and after fill g_i^post (=1 if full, else g_i^pre + V_i/C, or user-entered):
- Fuel in tank right after fill i: T_i = g_i^post · C
- Fuel in tank right before fill j: T_j^pre = g_j^pre · C
- Burned between fill i and next fill j: F_ij = T_i − T_j^pre = C·(g_i^post − g_j^pre)
- Consumption_ij = 100·F_ij / (O_j − O_i)
- Consistency check: V_j ≈ C·(g_j^post − g_j^pre); if residual > ~10% of C, flag a likely gauge or capacity error and ask to recalibrate C (useful capacity often differs from brochure capacity).
- Gauge error: needle readings are coarse (≈1/8 tank resolution, nonlinear senders). Error on F_ij is ≈ C·(σ_post + σ_pre). For a 60 L tank and ±1/16 reading error, ±~7.5 L — too large for short segments. So treat gauge-based values as estimates (show "~" and lower weight) and prefer exact full-to-full when available. Inverse-variance weighting when combining: w_s = 1/σ_s², where σ for full-to-full segments is small (brim-fill repeatability ~1-2 L) and σ for gauge segments is large.
- Kalman-style refinement (optional): state = fuel in tank; each fill adds V_i exactly; consumption rate is a slowly varying parameter; gauge readings are noisy observations. Yields smooth estimates and handles partial-only users who never brim-fill.

E. Odometer validation: reject or prompt when O_j ≤ O_i (non-increasing), when implied consumption is outside plausible band for the vehicle (e.g. <2 or >40 L/100 km), or when D exceeds C ÷ (min plausible consumption) (range-exceeding — implies a missed fill). Suggest "Did you miss a fill-up?" instead of silently computing.

F. Recompute policy: consumption is derived data. Store raw fills only; recompute all segments deterministically on any insert/edit/delete or back-dated entry (sort by odometer, tie-break date) — avoids stale values after edits/imports.

### Inferences
- Fuelio's complaints are UX as much as math: users do not understand why a partial gives no number. Show segment state visibly ("partial — waiting for next full tank, 2 fills pending") and a per-fill explanation popover with the formula and inputs used.
- Default new fills to "Full tank" ON, with auto-suggest "Partial?" when V_i is much smaller than typical fill or when user entered price-based amount (e.g. round AED 50/100 amounts in UAE suggest partial).

### Gaps
- Could not read Fuelio's FAQ page in full to confirm exactly how it handles missed fills and whether averages are distance-weighted.
- No peer-reviewed literature found on gauge-sender accuracy; ±1/16 error figure above is an illustrative assumption, not sourced.

## 3. No tank-level entry; PHEV/EV handling (free charging, small kWh, home vs public, charging losses)

### Takeaway
Fuelio assumes full-tank fills and only recently added tank-level % and "free" charging entries at user request. A competitor should model energy per powertrain, with explicit SoC/tank levels, multiple charge locations/tariffs, zero-cost charges, decimal kWh, and configurable wall-to-battery efficiency.

### Cited Findings
- iOS release notes: "Fixed: Possibility to add FREE fill-ups (charging) (requested by EV users)"; electric listed among fuel types — [App Store (snippet)](https://apps.apple.com/gt/app/fuelio-fuel-log-mileage/id1487753318?l=en-GB)
- Tank level %: "Showing % when setting tank level (as requested)" — same source
- PHEV batteries typically 10-20 kWh, EVs 60-100+ kWh; PHEV onboard charging 3.3-7.2 kW — [GreenLancer](https://www.greenlancer.com/post/charging-a-phev)
- Public charging is costly for PHEVs given low mileage per kWh; home charging is where PHEVs make financial sense — [Fleet News commentary](https://www.fleetnews.co.uk/news/fleet-industry-news/2019/05/09/bp-to-launch-combined-fuel-and-ev-charge-card-this-summer)
- Charging losses: Level 1 ≈ 80-84%, Level 2 ≈ 89-94% efficient — [Recurrent](https://www.recurrentauto.com/research/why-doesnt-your-battery-get-all-the-energy-you-pay-for), [InsideEVs](https://insideevs.com/features/711659/ev-charger-efficiency-losses/)
- ADAC tests: household socket lost 12.7-24.2%; 11 kW wallbox 5.1-7%; DC losses 5-15% (1-4% when preconditioned) — [The Next Web on ADAC](https://thenextweb.com/news/adac-ev-home-charging-losses-wallbox-socket)
- Tesla: 120V at best ~85%, as low as 60% in cold weather — [Yahoo Autos "EVs Explained: Charging Losses"](https://autos.yahoo.com/evs-explained-charging-losses-160100480.html)

### Inferences (design)
- Data model: vehicle has 1..n energy stores (fuel tank litres, battery kWh). Each "energy event" = {store, amount, unit, price_per_unit (nullable / 0 allowed), total_cost, location_type: home | work | public AC | public DC | free/destination, tariff_id, meter_source: wall | car, SoC_before, SoC_after, level_before/after for fuel}.
- Allow amount precision to 0.001 kWh/L and amounts < 1 kWh (topping up PHEVs); total cost 0 explicitly valid, with "free" not breaking cost stats (cost per km includes zero-cost energy).
- EV consumption, two views:
  - Wall-to-wheel (what you pay for): kWh_wall / D. Home charges logged from wallbox/smart plug meter are wall energy.
  - Battery-to-wheel (what the car shows): kWh_batt / D, where kWh_batt = C_batt·(SoC_after − SoC_before) or kWh_wall·η.
  - η default by charger type (L1 0.80, L2 0.90, DC 0.93, editable; ADAC/Recurrent ranges above), or learned per vehicle: η̂ = Σ C_batt·ΔSoC / Σ kWh_wall over charges with both values.
- EVs almost never "fill full": SoC-to-SoC is the natural analogue of tank-level estimation. Energy used between charge i and j = C_batt·(SoC_i^after − SoC_j^before). Usable C_batt must be configurable (and degrade over time).
- PHEV blended metrics: report separately L/100 km (fuel), kWh/100 km (electric), plus blended cost per km = (fuel cost + electric cost) / D and "electric share" = electric km ÷ total km. Electric km cannot be inferred from odometer alone; take from trip computer entry, OEM API, or estimate via energy split: total consumption attributed using per-mode baselines. Mark as estimate.
- Home charging cost: tariff support (flat, TOU — e.g. DEWA slab tariffs in Dubai), with "home charge from smart meter/wallbox import" CSV; auto-generate recurring home charge entries from monthly kWh if user only knows monthly totals.
- UAE angle: UAE public chargers (e.g. DEWA EV Green Charger, UAEV, Tesla) have per-kWh pricing; some destination chargers free — free and public must coexist.

### Gaps
- No Fuelio user reviews specifically about kWh rounding/small values surfaced (searches returned none); the "small kWh" weakness remains unverified by evidence here.
- UAE charger tariffs not researched in this note.

## 4. Too many taps to add a fill-up — fast-entry patterns

### Takeaway
Users of fuel logs add an entry at the pump with one hand; the fix is a 2-3 interaction capture path surfaced outside the app (widget, Control Center, Siri/App Intents, Android shortcuts/Tiles) plus OCR of pump display, receipt, and odometer, with smart defaults.

### Cited Findings
- iOS widget and Live Activity buttons run out-of-process and must use Button(_:intent:) / Toggle(_:isOn:intent:) backed by App Intents — [Ben Frearson blog](https://bfrearson.github.io/blog/ios-live-activties/), [Blake Crosley iOS 26 widget surface](https://blakecrosley.com/blog/ios-26-widget-and-control-surface)
- In iOS 26, Button(intent:)/Toggle(intent:) work in Lock Screen Live Activity and expanded Dynamic Island; LiveActivityIntent (iOS 17.2+) runs in the app process — [swiftcrafted Live Activities iOS 26 guide](https://swiftcrafted.dev/article/live-activities-dynamic-island-ios-26-swiftui-activitykit-guide)
- One App Intent can power widgets, Controls (iOS 18 Control Center / Lock Screen / Action button), Siri, Shortcuts, Spotlight — [Blake Crosley](https://blakecrosley.com/blog/ios-26-widget-and-control-surface); App Intents iOS 26 adds Visual Intelligence integration and interactive snippets — [Blake Crosley App Intents 2.0](https://blakecrosley.com/blog/app-intents-2-ios-26-additions)
- Apple WWDC24 sessions on App Intents/widgets — [WWDC24 10098](https://developer.apple.com/videos/play/wwdc2024/10098/), [WWDC24 10205](https://developer.apple.com/videos/play/wwdc2024/10205/)
- Fuelio already offers Live Activities and CarPlay for Trip Log — [App Store (snippet)](https://apps.apple.com/us/app/fuelio-gas-log-mpg-tracker/id1487753318)
- ML Kit Text Recognition v2: on-device, Latin/Chinese/Devanagari/Japanese/Korean; returns blocks/lines/elements with bounding boxes, rotation, confidence; suggested for receipts — [Google ML Kit docs](https://developers.google.com/ml-kit/vision/text-recognition/v2)
- Bundled model works offline immediately; ML Kit is OCR not a receipt parser — amounts need own parsing and a user review step; SDK may contact servers for model updates/metrics — [Atomic Robot](https://atomicrobot.com/blog/mlkit-on-device-ocr-android/), [DEV Community KMP OCR](https://dev.to/prem_singh_1997/from-pixels-to-text-google-ml-kit-ocr-on-android-with-a-kmp-core-25kh)
- Example KMP receipt parsing core over ML Kit — [ReceiptLensKMP](https://github.com/premsinghsodha7/ReceiptLensKMP)

### Inferences (design)
- Target: ≤3 taps from lock screen to saved fill. Pattern:
  1. Entry points: iOS interactive widget "+ Fuel", Control Center/Action-button Control, Siri phrase ("Log 45 litres for 120 dirhams"), Lock Screen widget; Android home-screen widget, app shortcut (long-press icon), Quick Settings Tile, Google Assistant App Actions; CarPlay/Android Auto "I'm refuelling" button.
  2. Prefilled form: vehicle = last used / connected via Bluetooth; date/time now; station = nearest from GPS; fuel type and unit price = last at this station (or today's UAE monthly regulated price — UAE fuel prices are set monthly by the fuel price committee, making price prefill highly accurate); full-tank ON.
  3. User enters only two of {litres, price/L, total} (third computed) plus odometer. Odometer prefilled with predicted value = last odometer + avg daily km × days, shown as hint, cursor on last 3-4 digits.
  4. Camera shortcuts: pump display OCR (7-segment digits — on-device text recognition plus digit-specific post-processing; regex on "LITRES"/"AED" labels), receipt OCR (ADNOC/ENOC/Emarat receipts: total, litres, unit price, VAT 5%, TRN, date), dashboard odometer OCR (choose largest 5-6 digit number near "km"; validate monotonic and within predicted range). iOS: Apple Vision VNRecognizeTextRequest / Live Text DataScanner (VisionKit) — on-device; Android: ML Kit v2.
  5. Always a confirmation sheet with confidence-highlighted fields (low-confidence fields yellow) — never silent save from OCR.
- Two-phase capture: "Start fill" at pump (Live Activity shows timer and prefilled station), finish by snapping receipt later — supports drivers who cannot type at pump.
- Voice: App Intent with parameters (litres, amount, odometer) and parameter resolution; Android via App Actions/BII or in-app speech-to-text with numeric grammar. Support Arabic numerals and English.
- Passive capture: import bank/card notifications or Apple Wallet transactions (where permitted), ADNOC/ENOC loyalty app exports, or email receipts, to auto-create draft fills for later confirmation.

### Gaps
- No measured tap counts for Fuelio's add-fill flow found (no review quoted exact taps). Claim "too many taps" not directly evidenced by retrieved reviews.
- OCR accuracy on 7-segment pump displays not benchmarked in sources found.

## 5. Data loss after phone reset/new phone — local-first sync and backups

### Takeaway
Fuelio stores data on-device only, has no servers, and relies on optional manual/auto Google Drive/Dropbox/iCloud Drive backups; users who skipped backups lost years of data and Fuelio cannot restore. A competitor should be local-first with always-on account or platform sync plus automatic verified backups.

### Cited Findings
- Fuelio FAQ: no servers; cannot restore data for users without a backup — [fuel.io backup FAQ (snippet)](https://fuel.io/faq_backup_help.html)
- Terms: data stored on device; developer disclaims loss from uninstalling, device failure, or third-party backup problems — [fuel.io terms (snippet)](https://fuel.io/termsofuse.html)
- 2023 App Store review: new phone, "all my data for the past year is now lost", asks why not backed up to iCloud — [App Store reviews (snippet)](https://apps.apple.com/us/app/fuelio-gas-log-mileage/id1487753318?see-all=reviews&platform=iphone)
- Current iOS notes: data stays on device with optional automatic iCloud Drive backup; listing also mentions iCloud sync across iPhone/iPad — same source family [App Store](https://apps.apple.com/us/app/fuelio-mpg-mileage-tracker/id1487753318)
- Review title "Backup all is not working"; aggregator summarizes Google Drive backup and import errors — [apptail Netherlands (snippet)](https://apptail.io/app/fuelio-fuel-log-mileage-YAt/netherlands), [grand-screen reviews](https://grand-screen.com/apps/fuelio-gas-log-gas-prices/reviews/)
- Competitor blog (biased) claims Fuelio auto-backups "silently stopped running" — [Brimly (snippet, not neutral)](https://brimlyauto.com/blog/fuelio-alternative/)
- Comparable failure: FuelTracker dev said data was not stored where iTunes backup could carry it; fixed with account sync — [App Store FuelTracker (snippet)](https://apps.apple.com/app/id964674334)
- Sync engines: PowerSync — SQLite on device, Postgres/Mongo/MySQL backend, SDKs Flutter/React Native/Swift/Kotlin, bidirectional with upload queue; most sources favor it for mobile; ElectricSQL is Postgres logical replication to clients, read-path ("shapes") sync with writes via your API (sources disagree), with reported rough edges Feb 2026 — [Kanopy Labs 2026](https://kanopylabs.com/blog/electric-sql-vs-powersync-vs-livestore-local-first), [QueryPlane](https://queryplane.com/blog/electricsql-vs-powersync-vs-replicache/), [BuildPilot 2026](https://trybuildpilot.com/648-electric-sql-vs-powersync-vs-zero-2026)
- Local-first architecture overview (2026) — [Smashing Magazine](https://www.smashingmagazine.com/2026/05/architecture-local-first-web-development/)

### Inferences (architecture)
- Data shape suits simple sync: append-mostly records (fills, expenses, trips) per user, rarely edited concurrently. Full CRDTs (Automerge/Yjs) are overkill; use row-level last-writer-wins with per-field timestamps (or hybrid logical clocks) and soft deletes (tombstones). Derived data (consumption) recomputed locally, never synced — eliminates most conflicts.
- Recommended stack options:
  - A. SQLite (Room/SQLDelight/GRDB) + PowerSync + Supabase Postgres (auth, RLS). Offline writes queued, works on iOS/Android/web. Good fit for shared family/fleet vehicles later.
  - B. Platform-native: CloudKit (iOS, via CKSyncEngine iOS 17+) + Google Drive appDataFolder/Firestore (Android). Zero-account UX but cross-platform migration (iPhone to Android — common) breaks; needs export bridge. Not recommended alone.
  - C. Firebase Firestore with offline persistence: easy, but cache is not a true local DB, query/cost limits, vendor lock-in.
- Regardless of sync: automatic encrypted daily snapshot (SQLite file + photos) to user's iCloud Drive/Google Drive, keep last N versions, show "Last backup: 2 h ago" on home screen and warn if >7 days; verify backup integrity by test-restore checksum. On iOS, store DB in app container included in iCloud device backup (not excluded); on Android enable Auto Backup (android:allowBackup with dataExtractionRules) so the 25 MB per-app Google backup restores on new phone.
- Onboarding restore prompt: on first launch detect existing cloud backup/account and offer restore before creating empty vehicle.
- Open export/import: CSV and Fuelio CSV import (Fuelio exports CSV) for switching users — key acquisition lever.
- Account optional (anonymous/Sign in with Apple/Google), but sync on by default once signed in.

### Gaps
- Fuelio's exact current backup defaults (auto vs manual) not confirmed; Fuelio pages unreachable.
- Official PowerSync/ElectricSQL docs not fetched; comparisons are secondary blogs, and ElectricSQL write model claims conflict.

## 6. Pricing fairness (Pro subscription)

### Takeaway
Fuelio moved formerly basic features behind a Pro subscription (~June 2023; ~US$4.99/month, $17.99/year; no lifetime), triggering "everything interesting is behind Pro" reviews and a Dec 2025 dispute over cost logging/history access. A competitor should keep the core log free forever, offer a lifetime option, and unbundle.

### Cited Findings
- App Store IAPs: Fuelio Pro 1-Year $17.99, 1-Month $4.99; other regions $14.99/yr, €14.99/yr (NL), CA$19.99/yr — [App Store US](https://apps.apple.com/us/app/fuelio-mpg-mileage-tracker/id1487753318), [App Store SN](https://apps.apple.com/sn/app/fuelio-fuel-log-mpg-tracker/id1487753318), [apptail NL/CA snippets](https://apptail.io/app/fuelio-fuel-log-mileage-YAt/netherlands)
- Subscription introduced June 2023, no lifetime option — [Brimly (competitor blog, treat with caution)](https://brimlyauto.com/blog/fuelio-alternative/)
- Reviews: "every remotely interesting feature is now behind a Pro plan", user moved to spreadsheets; developer reply: new features are paid, "the only way to move the project forward"; other reviewers: "price is fair, developers need to make a living" — [mwm.ai aggregator](https://mwm.ai/apps/fuelio-gas-log-mileage/1487753318)
- Dec 2025 review: cost logging moved behind monthly paywall and historical data no longer accessible; developer replied "not true" — [App Store reviews (snippet)](https://apps.apple.com/us/app/fuelio-gas-log-mileage/id1487753318?see-all=reviews&platform=iphone)
- Fuelio Pro marketed with gas-finder and route planning (Sygic) — [autoevolution](https://www.autoevolution.com/news/fuelio-pro-launches-as-the-most-advanced-gas-finder-and-route-planning-app-217106.html)
- Fuelio has no ads (Play listing shows no ads; iOS "No account required, no ads") — [Google Play](https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en_US)

### Inferences (pricing design)
- Bundling complaint: Pro bundles Sygic-style gas-finder/route features with logging features; users who only want logging pay for navigation. Unbundle: free core; "Plus" for power-user logging; separate optional add-ons (cloud photo storage, fleet).
- Free forever: unlimited fills/expenses/vehicles (or ≥2 vehicles), correct consumption, export, local backup. Never paywall export or read access to existing history (paywalling history caused the angriest reviews).
- Grandfathering: features free at time of use stay free for that user; announce changes in-app with dates.
- Offer: monthly, yearly, and lifetime (e.g. ~2.5-3x yearly) — lifetime is commonly requested in utility-app reviews. Server-side costs (sync, OCR if cloud) justify subscription only for cloud features; keep OCR on-device to avoid recurring cost.
- Regional pricing for UAE (AED tiers) and family sharing (Apple Family Sharing for IAP/subscriptions).

### Gaps
- No Reddit threads on Fuelio pricing surfaced; no confirmation of exact June 2023 date beyond a competitor blog.

## 7. Other weaknesses found

### Takeaway
Beyond the listed items, the dominant complaints are inaccurate/stale crowdsourced fuel prices, ignored fuel-type filters, sparse station data, unreliable trip logging on some phones, and narrow scope (fuel-only per AlternativeTo listing).

### Cited Findings
- Google Play reviews: station price data "mostly just not there, what data it has is usually wrong"; map shows many businesses but few stations; diesel preference ignored, "mostly all I ever see are gas prices"; German App Store: prices days old — [Google Play listing (snippets)](https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en_IN), [App Store DE reviews](https://apps.apple.com/de/app/1487753318?l=en-GB&see-all=reviews&platform=iphone)
- Rappler (Philippines): prices not updated fast enough during price spike — [Rappler](https://www.rappler.com/technology/features/fuelio-fuel-management-app-impression/)
- Developer: coverage depends on crowdsourcing; App Store disclaims accuracy — [App Store listing](https://apps.apple.com/us/app/fuelio-mpg-mileage-tracker/id1487753318)
- AlternativeTo lists Fuelio as not tracking other costs than fuel; Drivvo voted top alternative; Simply Auto freemium with maintenance/expense log — [AlternativeTo Fuelio](https://alternativeto.net/software/fuelio)
- Trip log failures blamed on manufacturer battery limits — [UserEcho (snippet)](https://fuelio.userecho.com/users/1-adrian/comments/?page=5)
- iOS: cannot edit entries; photos not importing between devices — [apptail Czechia (snippet)](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/czechia)

### Inferences
- UAE advantage: retail fuel prices (Super 98, Special 95, E-Plus 91, Diesel) are set nationally each month, so price data can be exact without crowdsourcing — auto-update on the 1st of each month from the official announcement. Turns Fuelio's biggest non-logging weakness into a strength locally. (Verify current official source in a separate note.)
- Filter by fuel grade must be respected everywhere (stats, station map, price prefill).
- Expenses/maintenance (Salik tolls, parking, RTA registration renewal, insurance, service) and reminders should be core, not Pro.
- Photo attachments must sync with records (store in object storage keyed by record ID, lazy download).

### Gaps
- Searches for Fuelio on Reddit (r/cars, r/dubai), Trustpilot and UserEcho returned no usable threads (UserEcho unreachable). No crash-rate data found; only changelog "Fixed some crashes".
- Review dates on several Google Play snippets could not be confirmed.
