# Outbuild Fuelio with exact UAE prices

The winning move is a local-first, bilingual (Arabic/English) vehicle-cost app that treats the UAE's **nationally fixed monthly fuel price** as its core asset. That price makes three things exact where Fuelio relies on crowdsourcing: price-per-litre prefill at the pump, retroactive cost history, and a UAE-only route-cost estimator. The app should then fix Fuelio's four documented failures. Those are wrong consumption figures after partial or missed fills, an iOS app that lags Android, data lost on phone change because Fuelio has no servers, and a paywall that moved formerly basic features behind a ~US$17.99/year subscription. Around that core, add the costs UAE drivers actually feel: Salik and Darb tolls, parking (Dubai residents report tolls plus parking can exceed fuel spend), Mulkiya and insurance reminders, and EV charging at the de facto national rate of AED 0.735 (AC) and AED 1.26 (DC) per kWh including VAT. A section-aware Fuelio CSV importer is the main acquisition lever. No mainstream competitor ships a robust one, and the file format is now well understood from public samples and parser code. Because the app is needed mainly on Android, the recommended build is native Kotlin + Jetpack Compose on a Kotlin Multiplatform shared core (engine, importer, tariffs, sync), with SQLite, PowerSync and Postgres hosted in a UAE cloud region. iOS follows later on the same core. Pricing is freemium with a lifetime option. Delivery runs in six phases over about 16 months. Android widgets, backups, SMS capture, trip detection and Android Auto come first, and iOS launches only at feature parity. Several tariff and price figures below come from secondary sources and are flagged; every tariff must ship as remote-configurable data, not code, because Salik, Darb and parking rules changed in 2024, 2025 and 2026.

## Fuelio's weaknesses are mostly architecture and policy, not missing features

Fuelio's own FAQ says it uses a **"full tank algorithm"** ([fuel.io FAQ](https://fuel.io/faq_fuel_consumption.html)). Partial fills therefore produce no figure or a distorted one. Fuelly users describe the same failure: after a partial, the app "calculated and displayed my new average mpg as if it had been a full fuel-up" ([Fuelly forum](https://www.fuelly.com/forums/f2/partial-fuel-up-distorts-mpg-17383-post182625.html)). The fix is well established. Simply Auto restarts the chain after a missed fill ([Simply Auto guide](https://simplyauto.app/userguide/fill-ups.html)). LubeLogger defers the calculation to the next full fill and shows "---" after a missed one ([LubeLogger docs](https://docs.lubelogger.com/Records/Fuel%20Records/)). An open-source spec formalises full-to-full with partial queuing, gauge fallback, chain breaks and batch recompute ([drivometry issue](https://github.com/ariful06/drivometry/issues/1)).

Platform parity is the second failure. iOS reviewers from 2022 to January 2026 cite a missing trip log, costs module, reminders and trip-cost calculation, and say entries cannot be edited ([apptail Canada](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/canada); [apptail Czechia](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/czechia)). The iOS written-review average sits at **3.0 stars across 553 reviews** ([mwm.ai](https://mwm.ai/apps/fuelio-gas-log-mileage/1487753318)). Fuelio has since added Trip Log to iOS with CarPlay and Live Activities ([App Store](https://apps.apple.com/us/app/fuelio-gas-log-mpg-tracker/id1487753318)). Whether costs and reminders reached iOS parity by 2026 is unconfirmed. The root cause is inferred from store IDs: two native codebases, with iOS arriving around 2019-2020, years after Android.

Data loss is the third failure. Fuelio has **no servers and cannot restore data for users without a backup** ([fuel.io backup FAQ](https://fuel.io/faq_backup_help.html)). One 2023 reviewer lost "all my data for the past year" after a phone change ([App Store reviews](https://apps.apple.com/us/app/fuelio-gas-log-mileage/id1487753318?see-all=reviews&platform=iphone)).

Pricing is the fourth. Fuelio Pro costs **$4.99/month or $17.99/year** in the US App Store ([App Store US](https://apps.apple.com/us/app/fuelio-mpg-mileage-tracker/id1487753318)). Sygic launched it in June 2023 at about €9/year with a 3-day trial ([Sygic press](https://www.sygic.com/press/fuelio-introduced-its-premium-cost-saving-features-for-planning-fill-ups-on-the-route-smarter)), so the price has risen or varies by region. Reviewers complain that "every remotely interesting feature is now behind a Pro plan" ([mwm.ai](https://mwm.ai/apps/fuelio-gas-log-mileage/1487753318)). A December 2025 reviewer claimed history access was paywalled, which the developer denied ([App Store reviews](https://apps.apple.com/us/app/fuelio-gas-log-mileage/id1487753318?see-all=reviews&platform=iphone)). Fuelio's crowdsourced station prices also draw complaints: "mostly just not there, what data it has is usually wrong," with diesel filters ignored ([Google Play](https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en_IN)).

| Fuelio weakness | Evidence | Fix in the new app |
|---|---|---|
| Wrong or missing average after partial/missed fills | [fuel.io FAQ](https://fuel.io/faq_fuel_consumption.html); [Fuelly forum](https://www.fuelly.com/forums/f2/partial-fuel-up-distorts-mpg-17383-post182625.html) | Full-to-full segments with partial accumulation, explicit missed-fill chain breaks, a distance-weighted average and an optional gauge/SoC estimate (formulas below). Show segment state ("partial, waiting for next full tank") and a per-fill "how was this calculated" popover. |
| iOS lags Android; no edit | [apptail Czechia](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/czechia) | One KMP shared core for every calculation, import and sync path. Android ships first; iOS ships on the same core only at parity, with a public parity matrix. Every record, including photos, is editable from day one. |
| Data lost on new phone | [fuel.io backup FAQ](https://fuel.io/faq_backup_help.html) | Local-first SQLite with optional account sync. Automatic encrypted daily snapshots to iCloud Drive/Google Drive. A "Last backup 2 h ago" indicator with a warning after 7 days. A restore prompt on first launch. Android Auto Backup is enabled. |
| Paywall moved basics behind Pro | [mwm.ai](https://mwm.ai/apps/fuelio-gas-log-mileage/1487753318) | Core logging, correct consumption, expenses, reminders, export and read access to history are free forever. Features that were free stay free for that user. Monthly, annual and lifetime plans. |
| Crowdsourced prices stale/wrong | [Google Play](https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en_IN); [Rappler](https://www.rappler.com/technology/features/fuelio-fuel-management-app-impression/) | The UAE has one official national price per grade per month, so the app ingests it on announcement day. No crowdsourcing is needed. |
| Fuel-only scope on some platforms | [AlternativeTo](https://alternativeto.net/software/fuelio) | Salik, Darb, parking, Mulkiya, insurance, fines and service are core categories with UAE presets. |
| Trip log breaks on OEM Android | [UserEcho](https://fuelio.userecho.com/users/1-adrian/comments/?page=5) | Bluetooth/CarPlay-connect auto-start, a foreground service with a notification, per-OEM battery guidance, and an odometer-only trip mode that needs no GPS. |
| No tank level; weak EV handling | [App Store notes](https://apps.apple.com/gt/app/fuelio-fuel-log-mileage/id1487753318?l=en-GB) | Energy stores per vehicle (tank in litres, battery in kWh), SoC/gauge before and after, explicit zero-cost charges, 0.001 precision, and a configurable charging efficiency. |
| "Too many taps" (claim not directly evidenced in retrieved reviews) | — | At most 3 taps: Glance widget, Quick Settings Tile or app shortcut (later iOS Controls/App Intents), then a prefilled form, then save. Optional OCR of the pump display, receipt and odometer. |

### Consumption formulas that survive partials, missed fills and edits

The notes derive the formulas below from the Fuelly, Simply Auto, LubeLogger, Drivvo and drivometry methods. The algebra is standard; it does not come from a single source. Notation for each fill *i*: odometer O_i, volume V_i, flags full_i and missed_i, optional gauge fractions g_i^pre and g_i^post, tank capacity C.

```
A. Full-to-full segment (a, b], consecutive full fills, no chain break between:
   D = O_b − O_a
   F = Σ V_i  for i = a+1 … b        (all partials in between plus V_b; V_a excluded)
   L/100km = 100·F / D     km/L = D / F     MPG(US) = D_mi / F_galUS
   Partials inside the segment show "pending"; the result applies to the whole segment.

B. Missed fill: if any fill k in (a, b] has missed_k = true → discard the segment.
   The next valid segment starts at the first full fill at or after k.
   The distance still counts for total km and cost/km, not for consumption.

C. Period / lifetime average (distance-weighted):
   Avg L/100km = 100 · Σ_s F_s / Σ_s D_s     over valid segments only
   Never: the mean of per-segment values. Never: include the first fill's litres.
   Monthly stats: prorate a straddling segment's fuel by the fraction of its distance inside the month.

D. Gauge (tank-level) estimate between fills i → j:
   F_ij = C · (g_i^post − g_j^pre)       consumption_ij = 100·F_ij / (O_j − O_i)
   Check: V_j ≈ C·(g_j^post − g_j^pre); a residual > ~10% of C → prompt to recalibrate C.
   Show the result as "~". Combine with full-to-full values by inverse-variance weight w = 1/σ².

E. EV / PHEV (SoC replaces gauge):
   kWh_batt = C_batt · (SoC_after − SoC_before)      kWh_batt = kWh_wall · η
   Wall-to-wheel kWh/100km = 100 · Σ kWh_wall / D    (what you pay for)
   Battery-to-wheel kWh/100km = 100 · Σ kWh_batt / D (what the car shows)
   Learned η̂ = Σ C_batt·ΔSoC / Σ kWh_wall   over charges that record both values
   PHEV blended cost/km = (fuel AED + electric AED) / D;  electric share = e-km / total km (estimate)

F. Validation: reject or prompt when O_j ≤ O_i, consumption is outside 2–40 L/100km,
   or D > C / (minimum plausible consumption) → ask "Did you miss a fill-up?"

G. Recompute: consumption is derived data. Store raw fills, sort by odometer (tie-break on date),
   and recompute every segment deterministically after any insert, edit, delete or import. Never sync derived values.

H. Cost per km = Σ all costs in period (fuel + energy + tolls + parking + service + amortised fixed costs) / km in period
```

Default efficiency η by charger type uses measured loss ranges. Level 1 charging is about 80–84% efficient and Level 2 about 89–94% ([Recurrent](https://www.recurrentauto.com/research/why-doesnt-your-battery-get-all-the-energy-you-pay-for)). ADAC measured 12.7–24.2% loss on household sockets, 5.1–7% on 11 kW wallboxes and 5–15% on DC ([The Next Web on ADAC](https://thenextweb.com/news/adac-ev-home-charging-losses-wallbox-socket)). Suggested defaults are 0.80 for L1, 0.90 for L2 and 0.93 for DC, all editable. The ±1/16-tank gauge error used to size σ for gauge estimates is an illustrative assumption with no source. Treat gauge-based numbers as estimates.

One UAE-specific heuristic belongs in the add-fill form. Drivers often buy round amounts (AED 50 or 100), so a round total, or a volume well below the usual fill, should trigger a "Partial fill?" suggestion. Full tank stays the default.

## Thirteen rivals and eight UAE apps supply the feature list

The global market splits into classic loggers (Drivvo, Fuelly, Simply Auto, Spritmonitor, Road Trip MPG, Fuel Buddy), mileage-tax trackers (MileIQ, Driversnote, Everlance, Stride), free ownership super-apps (CARFAX Car Care, Jerry), self-hosted tools (LubeLogger, Hammond) and connected-car/EV/OBD apps. A 2025–2026 wave of AI apps (Carrus, UpKeep Auto, PitSync, AutoFix, Docaro) all converge on receipt scanning and an "AI mechanic." Core logging is commoditised. What still differentiates apps is capture speed, imports, sync and sharing, reminders and reports. CARFAX Car Care shows the strongest UX signal: a free "what's due" dashboard rated about **4.8 on the App Store and 4.7 on Play** ([AAA](https://www.acg.aaa.com/connect/blogs/4c/auto/best-car-maintenance-apps); [CARFAX](https://www.carfax.com/service/)).

| Borrow | From | Why it matters / UAE adaptation |
|---|---|---|
| Receipt-to-record AI that also sets the next-due date | UpKeep Auto ([App Store](https://apps.apple.com/us/app/upkeep-auto/id6747728359)), Carrus ([App Store](https://apps.apple.com/app/id6770422802)) | Table stakes among newcomers. Train and test on ADNOC/ENOC/Emarat and Arabic garage receipts, with 5% VAT and TRN fields. |
| "What's due" dashboard with odometer and date reminders | CARFAX ([CARFAX](https://www.carfax.com/service/)), LubeLogger ([site](https://hargata.github.io/lubelog)) | The anchor date is the Mulkiya expiry, with chained inspection, insurance and fines tasks. |
| Quick Entries for repeat logging | Hammond ([GitHub](https://github.com/alfhou/hammond)) | Templates for "Salik top-up", "ENOC full tank 95", "Parkin 2 h". |
| Import from rivals | Fuelio imports ~10 apps ([AlternativeTo](https://alternativeto.net/software/fuelio)) | Fuelio first, then Drivvo, Fuelly and generic CSV. |
| One-time unlock plus multi-driver tiers | Simply Auto GOLD/PLATINUM ([App Store](https://apps.apple.com/us/app/id893278325)) | Lifetime plan, and family sharing for household cars and drivers. |
| Automated weekly/monthly reports; printable vehicle history | Simply Auto, LubeLogger ([SelfHostYourself](https://selfhostyourself.com/services/lubelogger)) | Resale "service history PDF" in Arabic and English, useful in the UAE used-car market (inference). |
| Automatic trip detection and business mileage reports | MileIQ (40 free drives/month), Driversnote beacon ([SparkReceipt](https://sparkreceipt.com/blog/best-mileage-tracker-apps/)) | Opt-in premium feature for reimbursement claims, with Salik and parking attached to the trip. |
| Community consumption benchmarking by model | Fuelly.com ([AlternativeTo](https://alternativeto.net/software/fuelly)) | "Your Patrol vs other UAE Patrols, summer vs winter". Needs an opt-in, anonymised aggregate. |
| Document vault without tight caps | Fuelly premium attachments; Drivvo's 250 MB cap drew complaints ([Capterra](https://www.capterra.com/p/219368/Drivvo/)) | Mulkiya, insurance policy, warranty and service-contract scans. |
| Transparent, editable automation rules | Copilot Money (Apple Design Award finalist 2024; users complain rules are hidden) ([App Store](https://apps.apple.com/us/app/id1447330651); [FinCompareLab](https://www.fincomparelab.com/reviews/copilot-money-review/)) | SMS/receipt auto-categorisation rules are visible and editable. |
| EV charging logs | TezLab Pro $6.99/mo ([App Pricing Lab](https://apppricinglab.com/iap/apple/1239581716)); ABRP users complain about missing charger pricing ([ABRP](https://abrp.featurebase.app/p/tesla-chargers-seem-excluded-and-have-no-tesla-pricing)) | Ship UAE network tariffs (DEWA, UAEV, Tesla, E2GO) prefilled. |
| AI mechanic chat; OBD-II | Carrus, PitSync; FIXD ~$99/yr, criticised for locked live data ([iamcarhacker](https://iamcarhacker.com/?p=15082)) | Low priority. Liability risk, and users dislike paywalled OBD data. |

The UAE local apps are all closed, with no documented public APIs for user data. Integration therefore means deep links, SMS/email/receipt parsing and open data ([Dubai Pulse catalog](https://www.dubaipulse.gov.ae/data/smartdubai-general/smart_dubai_dubai_pulse_data_catalog_open-open)). The ADNOC app offers MobilePay, a Wallet, Rewards (+25% points when paying in app) and Fill & Go plate recognition ([ADNOC Distribution](https://www.adnocdistribution.ae/consumer-fuel); [ADNOC Rewards](https://www.adnocdistribution.ae/rewards)). ENOC Pay sends transaction notifications and digital receipts, which can be captured by forwarding them to the app ([App Store](https://apps.apple.com/us/app/enoc-pay/id1395158011)). The parking SMS shortcodes produce confirmation messages: **7275 in Dubai, 3009 in Abu Dhabi and 5566 in Sharjah**. Dubai's format is `<Plate> <Zone> <Hours>` ([Dubizzle](https://www.dubizzle.com/blog/cars/parkin-app-dubai/); [Dubizzle AD](https://www.dubizzle.com/blog/cars/abu-dhabi-sms-parking-payment/); [Westgate Dubai](https://westgatedubai.com/sharjah-parking-sms-your-ultimate-guide-to-paying-with-5566/)). The cleanest pattern is an in-app **parking composer**. It opens `sms:7275?body=<plate> <zone> <hours>` with the stored plate and logs the cost from the known tariff, which beats parsing. Android SMS parsing is a second layer, constrained by Google Play's READ_SMS restrictions, and iOS cannot read SMS at all. Salik deducts some Parkonic parking fees from the Salik account ([Gulf News](https://gulfnews.com/living-in-uae/transport/where-you-can-use-salik-to-pay-for-parking-in-the-uae-1.500198394)). Any Salik-debit parser must therefore split toll from parking. A Salik top-up is a prepaid transfer, not a cost.

Some locally relevant integration ideas lack sourced evidence: Apple Wallet or bank-notification capture, the exact formats of Salik and parking confirmation SMS and their sender IDs, and Emarat app features. Validate them with real user samples before building.

## Importing Fuelio data: one vehicle per file, seven sections, header-name mapping

No official Fuelio CSV spec is published. The reference below comes from two real backups (2018 Czech km/litres, 2024 UK miles/litres) and five open-source parsers. A Fuelio backup holds **one vehicle per file**, `vehicle-<N>-sync.csv`, usually zipped as `vehicle-<N>-sync.csv.zip`. The vehicle id exists only in the filename ([tigattack fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py)). Inside the file, single-cell marker rows `"## <Name>"` introduce sections, and each marker is followed by its own header row. Standard `csv.DictReader` breaks because it treats `## Vehicle` as the header ([tigattack README](https://github.com/tigattack/lubelogger-fuelio-importer)). On Android, files live in `backup-sync` and `backup-csv` under local `/Fuelio`, Google Drive `Android/Fuelio` or Dropbox `Apps/Fuelio` ([Fuelio FAQ](https://fuel.io/faq_backup_help.html), via search snippet only). The iOS app says it "is able to read and export data to CSV format" ([App Store](https://apps.apple.com/app/fuelio-gas-tracker-mpg/id1487753318)). Whether iOS files match the Android sync format is **unverified**.

### Section and column reference

| Section | Columns in observed order | Notes |
|---|---|---|
| `## Vehicle` | Name, Description, DistUnit, FuelUnit, ConsumptionUnit, ImportCSVDateFormat, VIN, Insurance, Plate, Make, Model, Year, TankCount, Tank1Type, Tank2Type, Active, Tank1Capacity, Tank2Capacity, FuelUnitTank2, FuelConsumptionTank2 | 18 columns in 2018, 20 in 2024. Optional `guid` and `lastupdated` appear in some files ([flio_models.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/flio_models.py)). Older import-format files have 16 columns, ending at Active. |
| `## Log` | Data, Odo (km\|mi), Fuel (litres), Full, Price (optional), l/100km (optional) \| mpg (optional), latitude (optional), longitude (optional), City (optional), Notes (optional), Missed, TankNumber, FuelType, VolumePrice, StationID (optional), ExcludeDistance, UniqueId, TankCalc | 18 columns. `Price` is the **total** cost; `VolumePrice` is the unit price (46.301 × 1.449 = 67.09). `City` actually holds a station label. The consumption column is often `0.0`: ignore it and recompute. Rows may be newest-first. |
| `## CostCategories` | CostTypeID, Name, priority, color | Defaults: 1 Service, 2 Maintenance, 4 Registration, 5 Parking, 6 Wash, 7 Tolls, 8 Tickets/Fines, 9 Tuning; 31+ user-defined. Categories are global across cars. |
| `## Costs` | CostTitle, Date, Odo, CostTypeID, Notes, Cost, flag, idR, read, RemindOdo, RemindDate, isTemplate, RepeatOdo, RepeatMonths, isIncome, UniqueId | Exclude `isTemplate=1` from booked costs. Map `isIncome=1` to income. Turn the Remind/Repeat fields into reminders. |
| `## FavStations` | NameBrand, Latitude, Longitude, StationID, Description, CountryCode | Joins to Log `StationID`. |
| `## Category` | IdCategory, Name | Trip/usage categories (1 Private, 2 Work). |
| `## TripLog` | Partial list only: StartDate, EndDate, StartOdo, EndOdo, TripDist, TripDuration, TripCost, TripNote, title/Title, plus coordinates and internal IDs | In newer backups only. **Full order unverified** ([HA-Fuelio parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py)). Treat as optional. |

Sources for the samples: [tigattack sample](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [dundee example-fuelio.csv](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv), [Programistyk fueliobackupbuilder](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/fueliobackupbuilder.class.php).

| Code field | Values |
|---|---|
| DistUnit | 0 = km, 1 = miles |
| FuelUnit | 0 = litres, 1 = US gal, 2 = UK gal |
| ConsumptionUnit | 0 = l/100km, 1 = MPG US, 2 = MPG UK, 3 = km/l, 4 = km/gal US, 5 = km/gal UK |
| FuelType (roots) | 100 Gasoline, 200 Diesel, 300 Ethanol, 400 LPG, 500 CNG, 600 Electricity, 700 Flex; 0 = unset, −1 = unknown |
| FuelType (children seen) | 110 "95", 111 "95+", 112 "98", 113 Ultimate, 114 "100", 119 E10, 201 Diesel, 202 Diesel+, 206 ULSD, 209 B20, 211 B100, 217 AdBlue, 305 E85, 401 LPG, 501 CNG, 601 AC 240V, 602 DC fast |

The unit codes come from [vehicle.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/vehicle.class.php) and the fuel codes from [FuelType.csv](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/FuelType.csv) and [tigattack fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py). The list has grown over time: codes 119, 218, 219, 502 and 503 are absent from the 2018 table. UK users mix miles with litres (DistUnit=1, FuelUnit=0), so units must be read per field, not inferred as a set. The mapping to UAE grades is **low confidence** and needs confirmation in the import preview. The proposal is 110/111 → Special 95, 112/113 → Super 98, a "91" child (reportedly 106) → E-Plus 91, and 2xx → Diesel. The file does not store a currency, so the importer must ask for it.

### Parser design

The existing parsers each break on something. tigattack hard-codes `Odo (mi)`, so km files fail its required-column check. dundee's converters parse by position and use `int(odo)`, so they crash on `"212477.0"` and on `HH:mm` times ([fuelio2spritmonitor](https://raw.githubusercontent.com/dundee/fuelio2spritmonitor/master/convert.py)). HA-Fuelio is strict and metric-only, but it contributes two useful rules: it is BOM-tolerant, and it clamps Fuelio's impossible recurring-cost dates such as `2026-09-31` to the month end ([HA-Fuelio](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py)). No mainstream app, including LubeLogger, Hammond and Fuelly, parses the sectioned file natively ([LubeLogger ImportMappers.cs](https://raw.githubusercontent.com/hargata/lubelog/main/MapProfile/ImportMappers.cs)). Drivvo Pro claims Fuelio restore, which is low confidence. A tolerant importer is therefore a genuine differentiator.

```
import(files[]):                                  # .csv, .csv.zip, .zip, many at once (one per vehicle)
  for f in expand_zips(files):                    # pick *.csv members; prefer newest modified time
    text  = decode(f, 'utf-8-sig')                # strips BOM; fallback cp1252 with a warning
    delim = sniff(text, [',', ';'])               # Excel re-saves in comma locales use ';'
    rows  = csv_reader(text, delim, quote='"', strict=False)
    sections = split_sections(rows)               # a row whose first non-empty cell, quote/comma-stripped,
                                                  # starts with '## ' opens a section; next row = header;
                                                  # skip blank rows; tolerate short/long rows
    hdr   = normalize(header)                     # lowercase; strip spaces, '(optional)', and the unit
                                                  # parenthetical → (key, unit): 'Odo (mi)' → ('odo','mi');
                                                  # matches 'Odo(km)' and 'Odo (km)'
    veh   = map_vehicle(sections['vehicle'])      # units from DistUnit/FuelUnit; header unit wins if they conflict
    fills = [map_log(r) for r in sections['log']] # by header name, never by position
      date:   try 'yyyy-MM-dd HH:mm:ss', 'yyyy-MM-dd HH:mm', 'yyyy-MM-dd',
              then ImportCSVDateFormat (Java → platform), then 'dd.MM.yyyy'; device-local time zone
      number: '.' decimal; fallback single ',' decimal; '1' or '1.0' are both true flags
      total/unit price: if one is 0 or missing, derive it from the other and the volume
      convert to km / litres; keep originals in import_raw for audit
      grade: FuelType → UAE grade via mapping table; mark 'needs review' if unmapped or −1
    costs = map_costs(sections['costs'], sections['costcategories'])
      skip isTemplate=1; isIncome=1 → income; clamp invalid dates to month end
      RemindDate/RemindOdo/RepeatMonths/RepeatOdo → reminder rows
      categories: match by Name to UAE presets (Parking, Tolls→Salik/Darb, Registration→Mulkiya…)
    stations = sections['favstations'] joined on StationID; trips = sections.get('triplog') (optional)
  dedupe: by (vehicle, UniqueId) else (date, odometer, volume); re-import is idempotent
  sort fills by odometer then date; recompute all consumption (formula G)
  preview: counts per section, unit and currency confirmation, grade mapping, anomalies
           (odometer going backwards, implausible L/100km), and a side-by-side average vs Fuelio's
  commit inside one transaction tagged with import_batch_id → one-tap undo
```

Build the test fixtures from the two public samples, the older comma-padded import format (`## Vehicle,,,,`) and synthetic edge cases. The edge cases should cover dual tanks, gallons, `;` delimiters, comma decimals, an unquoted empty field and newest-first ordering. None of the public samples shows dual tanks, gallons, `;` separators or comma decimals, so those paths are **unverified against real files**. Ask early users for anonymised backups.

## UAE specifics: one national price, many fast-changing tariffs

### Drop "stations on route" and use the monthly price as the core asset

Fuelio Pro bundles Sygic gas-finder and route planning ([autoevolution](https://www.autoevolution.com/news/fuelio-pro-launches-as-the-most-advanced-gas-finder-and-route-planning-app-217106.html)). That bundling is part of what logging-only users resent. In the UAE the feature has almost no value, because every major outlet reports **one UAE-wide price per grade per month**. No source states the uniformity explicitly, however, and no evidence of brand or emirate differences was found. The UAE has linked pump prices to international markets since 2015. A committee announces prices on the **last day of each month, effective the 1st** ([Arabian Business](https://www.arabianbusiness.com/abnews/uae-announces-new-fuel-prices-for-april-2026); [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html)). WAM carries the primary release ([WAM](https://www.wam.ae/en/article/178ypmj-uae-fuel-price-committee-announces-prices-for)). Retailer pages lag: ADNOC still showed Super 98 at AED 2.59 after the April 2026 change ([Arabian Business](https://www.arabianbusiness.com/business/retail/uae-petrol-prices-will-change-this-week)). No official open API or GitHub dataset exists, so the app must build its own.

| Month | Super 98 | Special 95 | E-Plus 91 | Diesel | Source |
|---|---|---|---|---|---|
| Nov 2025 | 2.63 | 2.51 | 2.44 | 2.67 | [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html) |
| Dec 2025 | 2.70 | 2.58 | 2.51 | 2.85 | [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates) |
| Jan 2026 | 2.53 | 2.42 | 2.34 | 2.55 | [Gulf News](https://gulfnews.com/business/energy/uae-petrol-diesel-prices-for-january-2026-announced-1.500393811) |
| Feb 2026 | 2.45 | 2.33 | 2.26 | 2.52 | [Gulf Today](https://www.gulftoday.ae/news/2026/01/31/fuel-prices-from-february-2026-in-uae-announced) |
| Mar 2026 | 2.59 | 2.48 | 2.40 | 2.72 | [Aletihad/WAM](https://en.aletihad.ae/amp/news/business/4648017/uae-fuel-price-committee-announces-prices-for-march) |
| Apr 2026 | 3.39 | 3.28 | 3.20 | 4.69 (EZHire: 4.62) | [Gulf News](https://gulfnews.com/business/energy/uae-announces-petrol-and-diesel-prices-for-april-2026-1.500491487); [EZHire](https://www.ezhire.ae/blog/petrol-price-in-the-uae) |
| May 2026 | 3.66 | not found | 3.48 | 4.69 | [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates) |
| Jun 2026 | 3.95 | not found | not found | 4.33 | [Dubai Fast Living](https://dubaifastliving.com/uae-fuel-prices-june-2026/) |
| Jul 2026 | 3.40 | not found | not found | 3.60 | [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates) |
| Aug 2026 | 3.60 | 3.49 | 3.41 | 3.80 | [Yallamotor](https://www.yallamotor.com/news/uae-fuel-prices-october-2026-analysis-55857) |
| Sep 2026 | 3.80 | 3.69 | 3.61 | 4.30 | [Yallamotor](https://www.yallamotor.com/news/uae-fuel-prices-october-2026-analysis-55857) |
| Oct 2026 | 4.40 | 4.28 | 4.21 | 4.80 | [Emirates 24/7](https://www.emirates247.com/uae/uae-fuel-prices-for-october-2026-announced/6171) |
| Nov 2026 | 4.16 | 4.04 | 3.97 | 4.75 | [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced) |

All values are AED per litre. **All rows come from search-result snippets of news and aggregator sites, not WAM primary releases.** Verify each against WAM before seeding production data.

The volatility is the product insight. Diesel jumped **AED 1.97 in one month** (2.72 to 4.69, April 2026), and each petrol grade rose about 80 fils, during the regional conflict ([Arabian Business](https://www.arabianbusiness.com/abnews/uae-announces-new-fuel-prices-for-april-2026)). Super 98 then ran from 2.45 in February to 4.40 in October. A route estimate made on the 28th for a trip on the 3rd can be off by a third. The app must therefore model price by effective month, not as one "current price."

The ingestion pipeline works as follows. Starting at 16:00 GST on the last day of each month, a scheduled job polls WAM for "Fuel Price Committee announces prices for {Month}". Gulf News and Khaleej Times headlines are the fallback. The job regex-extracts the four AED values and sanity-checks the change against the previous month: anything above AED 2.00 is flagged, since a 1.97 move really happened. It auto-publishes when two sources agree and otherwise queues for admin approval, then sends a push ("Super 98 down 24 fils for November"). The job retries until the 1st, and a manual admin screen is the last fallback. History from 2015 to 2025 can be back-filled by scraping the Gulf News/Khaleej Times monthly URL pattern ([Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates)). The 2022 peak (~AED 4.63 Super 98) and the July 2015 baseline (~AED 1.72) are prior knowledge only and **unverified**.

### Route cost estimator with month-aware pricing

```
fuel_cost   = distance_km × (L_per_100km / 100) × price[grade][month(trip_datetime)]
ev_cost     = distance_km × (kWh_per_100km / 100) × tariff(home | AC 0.735 | DC 1.26 | custom)
tolls       = Σ salik(gate, direction, time, weekday, holiday/Ramadan schedule, linked-gate rule)
            + Σ darb(gate, time, weekday, holiday)
total       = fuel_cost + tolls + parking_estimate(emirate, zone, hours, start time) + optional_service_fee
per-person  = total / passengers;   round trip = outbound + return with each leg's own time
```

Five rules make the estimator handle monthly price changes:

1. If month(trip_date) has a published price, use it. A trip planned today for next week uses the price in effect next week.
2. If the month is not yet announced, use the latest price, label it **"provisional"**, and show a sensitivity band. The band is the latest price ± the largest month-on-month move in the trailing 12 months (from the price table).
3. Saved trips subscribe to the announcement. On the last day of the month, the app re-prices them and notifies the user ("Your Abu Dhabi trip now costs AED 6 less").
4. An optional forecast is latest × (1 + Δ benchmark month-average). Outlets publish "set to rise/fall" pieces days ahead ([The National](https://www.thenationalnews.com/news/uae/2026/02/28/uae-fuel-prices-set-to-rise-in-march)), but the committee's formula is not public. Always label the forecast as an estimate.
5. L/100km comes from the user's own measured segments for that vehicle, using summer (Jun–Sep) values for summer trips. It falls back to the manufacturer figure. No sourced UAE number exists for the AC penalty, so derive it from user data rather than claiming a percentage.

Distance and the route polyline can come from any provider. Google Routes supports toll estimates via `extraComputations: TOLLS` ([Google](https://developers.google.com/maps/documentation/routes/calculate_toll_fees?hl=en)). A Dubai developer, however, had to ask Google to enable Dubai gates ([Google Cloud Community](https://www.googlecloudcommunity.com/gc/Developer-Tools/Request-for-Enabling-and-Identifying-Toll-Gates-in-Dubai-UAE-for/td-p/871835)), so UAE toll support is **unverified**. Compute tolls in-house instead: keep a gate-geofence table, snap the polyline and count directional crossings. Pricing differs by provider. Google Compute Routes Essentials includes 10,000 free events per month, then $5 per 1,000, and toll requests move into pricier SKUs ([gotime notes](https://gotime.readthedocs.io/en/stable/pricing.html)). Mapbox gives about 100,000 free requests per month, though sources conflict ([StoreRocket](https://storerocket.io/learn/mapbox-pricing)). Self-hosted OSRM on the GCC OpenStreetMap extract has zero marginal cost. Use Mapbox or OSRM for estimates and Apple MapKit on iOS. The gap is real: existing UAE tools such as the JobxDubai commute calculator and uaecalc's Salik calculator each cover only part of the problem ([JobxDubai](https://jobxdubai.com/tools/commute-calculator); [uaecalc](https://uaecalc.pages.dev/salik)). No app found combines a month-aware fuel price, route, Salik/Darb and parking, though the search was limited.

### Tolls, parking, registration and EV tariffs as remote-config tables

| Item | Current figure | Status |
|---|---|---|
| Salik gates | 10 gates since 24 Nov 2024 (added Business Bay, Al Safa South). Al Safa N+S in the same direction within 1 h is charged once ([Khaleej Times](https://khaleejtimes.com/uae/transport/dubai-salik-announces-2-new-toll-gates-to-be-operational-from-november-24?amp=1)) | Gate GPS not sourced; build from OSM. The Al Mamzar linked-gate rule is not re-verified. |
| Salik pricing | Peak 06–10 and 16–20 Mon–Sat; off-peak 10–16 and 20–01; free 01–06; Sunday flat off-peak. AED 6/4 pre-VAT, **AED 6.30/4.20 with 5% VAT** ([Selfdrive.ae](https://selfdrive.ae/blog/dubai-salik-charges-explained-how-much-will-you-pay-in-2026); [YallaMotor](https://www.yallamotor.com/news/salik-vat-uae-2026-5-percent-tolls-parking-55390)) | **VAT start conflicts:** 1 Jun 2026 vs 1 Jul 2026 ([PropertyFinder](https://www.propertyfinder.ae/blog/dubai-salik-tolls-commute-costs/)). Ramadan and Eid use special schedules. |
| Darb | AED 4 per peak crossing, 07–09 and 15–19 on working days. Sundays and holidays reported free. Caps removed 1 Sep 2025 per [AD Mobility](https://admobility.gov.ae/en/darb-daily-and-monthly-caps) | **Conflicts:** the ITC page and Shory still list the AED 16/day and AED 200/150/100/month caps. Chargeable days are reported as Mon–Sat or Mon–Fri. New 24/7 E11 gates (Ghantoot, Al Qurm, from 4 May 2026) are **unverified** ([Shory](https://www.shory.com/car-insurance/blog/new-darb-toll-gates-in-abu-dhabi-locations-timings-charges-guide/)). |
| Dubai parking (Parkin) | From 4 Apr 2025: premium AED 6/h at peak (08–10, 16–20), standard AED 4; event zones AED 25/h; Sundays and holidays excluded from peak ([WAM](https://www.wam.ae/article/biuz2vx-parkin-announces-variable-parking-tariff-policy)) | Possibly stale: Dubai reportedly raised charges again around June 2026 ([ZigWheels](https://www.zigwheels.ae/car-news/dubai-motorists-to-pay-more-from-june-1-check-new-salik-parking-charges)). |
| Abu Dhabi (Mawaqif) | Standard AED 2/h or AED 15/24 h; premium AED 3/h (max 4 h); SMS 3009 ([Shory](https://www.shory.com/blog/car-insurance/mawaqif-abu-dhabi-parking-types-fees-and-payment-methods)) | Secondary source. |
| Sharjah | SMS 5566 + 38 fils surcharge per SMS (AED 2.38 for 1 h) ([Westgate Dubai](https://westgatedubai.com/sharjah-parking-sms-your-ultimate-guide-to-paying-with-5566/)) | RAK, Ajman, Fujairah and UAQ not researched. |
| Mulkiya (Dubai) | AED 350 + AED 20 fee; inspection AED 150–170 for cars over 3 years; expired registration AED 500 fine; unpaid fines block renewal ([CarSwitch](https://carswitch.com/newsroom/rta-mulkiya-renewal-for-used-cars-in-the-uae-2026-requirements-costs-what-can-block-it/)) | The official RTA schedule was not read. Abu Dhabi has no AED 20 fee and ADNOC inspection costs ~AED 100–150. |
| Insurance | Must cover 12 months (13 in Sharjah); third-party from ~AED 600–640/yr; comprehensive ~1.5–3.2% of car value ([PolicyBazaar](https://www.policybazaar.ae/car-insurance.md)) | Indicative only. |
| Black points | Federal; 24 points in a year means suspension ([LookInsure](https://lookinsure.com/blog/dubai-black-points)) | Suspension length conflicts (3–6 months vs 1 year). |
| EV public | AC AED 0.70 (0.735 incl. VAT), DC AED 1.20 (1.26) across DEWA and UAEV; Tesla 1.26 flat with idle fees up to AED 4/min; ADNOC E2GO ≥1.26 with every 4th session free ([ZigWheels](https://www.zigwheels.ae/car-news/dewa-green-charger-guide-2026-how-to-charge-your-ev-in-dubai); [Plaidcars](https://www.plaidcars.com/tesla/tesla-charging-cost-uae/)) | Secondary sources. The DEWA free first year is unconfirmed, and DEWA's old 29 fils rate is outdated. |
| ADNOC attendant fee | Optional AED 10 "Flex" since 2018 ([Arabian Business](https://www.arabianbusiness.com/industries/energy/399536-adnoc-customers-must-pay-aed-10-or-pump-own-petrol-from-june-30)) | Later reports say it was scrapped ([Dubai Eye](https://dubaieye1038.com/news/local/adnoc-will-not-charge-dh10-to-fuel-vehicles)). Model it as a service-fee field defaulting to 0. |

Darb's removed caps turn it into a linear cost. A daily commuter making two peak crossings on about 22 days pays roughly **AED 176 a month**. That makes "leave 1 hour earlier and save AED X" a concrete insight. The same applies to Salik's AED 2.10 gap between peak and off-peak. Residents already report that tolls plus parking can cost more than fuel ([Gulf News](https://gulfnews.com/living-in-uae/transport/uae-residents-say-tolls-and-parking-fees-cost-more-than-fuel-heres-how-they-save-money-1.500193210)). The dashboard should therefore lead with total cost per km, not fuel economy alone.

Mulkiya expiry anchors the reminder chain. Thirty days before it, the app creates tasks to book inspection (if the car is 3+ years old), renew insurance (it must cover 12 or 13 months) and clear fines (a renewal blocker). Insurance is amortised monthly in total-cost-of-ownership views. Fines are logged manually with black points and the discount captured as original versus paid, plus a deep link to the MOI or Dubai Police app. No personal-fines API was found.

### Arabic, AED and PDPL

Localisation follows the notes. Currency uses `Intl.NumberFormat('ar-AE'|'en-AE', {style:'currency', currency:'AED'})` with 2 decimals, because fils matter (AED 2.38, 4.20). Users can choose Western or Eastern Arabic digits. The layout mirrors fully for RTL, including chart axes. Units default to km, litres, km/L and L/100km. The Gregorian calendar stays primary, because government expiries are Gregorian, with optional Umm al-Qura Hijri display (`ar-AE-u-ca-islamic-umalqura`). A Ramadan-aware tariff calendar covers Salik's special schedules ([ArabWheels](https://www.arabwheels.ae/blog/salik-toll-changes-ramadan-2026-dubai/)). Sunday is the transport "off day" for Darb, Parkin and Mawaqif.

The PDPL (Federal Decree-Law 45/2021) has applied since 2 January 2022. It is GDPR-like, requiring explicit and specific consent and banning bundled consent. Articles 22–23 govern cross-border transfers ([Bird & Bird](https://www.twobirds.com/en/insights/2021/uae/how-does-the-new-uae-federal-decree-law-on-personal-data-protection-compare-against-the-gdpr); [Kayrouz](https://www.kayrouzandassociates.com/insights/cross-border-data-transfers-under-uae-law-in-2026)). Its Executive Regulations and penalties were still reported pending in 2026 ([ConsentStack](https://consentstack.io/regulations/uae-pdpl)). No adequacy list exists yet ([Clifford Chance](https://www.cliffordchance.com/insights/resources/blogs/talking-tech/en/articles/2025/03/doing-business-in-the-middle-east--data-transfers-in-the-uae-and-ksa.html)). Data localisation applies only to sectors such as health and finance ([Middle East Briefing](https://www.middleeastbriefing.com/news/uae-data-protection-obligations-and-cross-border-data-transfer-for-businesses/)). DIFC and ADGM have their own regimes.

The design follows from that. Location traces and SMS content are high-sensitivity data, so each one gets its own opt-in. SMS is parsed on the device, and only the amount, date and type are kept. Users control retention and can export or delete everything. Do not collect Emirates ID numbers or card data. Host optional sync in a UAE region (AWS me-central-1 or Azure UAE North). That is not legally required, but it removes the transfer question and makes a marketing point. Whether a small consumer app must register with the UAE Data Office or appoint a DPO is **unresearched**. Get local counsel before launch.

## Recommended stack: Android-first Kotlin and Compose on a KMP core, iOS later

The app is needed mainly on Android, so build it as a **native Kotlin + Jetpack Compose app with its domain core in a Kotlin Multiplatform (KMP) shared module**. The KMP module holds the consumption engine, data model, Fuelio importer, tariff and route-cost engine and sync client. The Android app owns the UI and every OS integration. When iOS comes, it reuses the shared module unchanged. The iOS UI can then be Compose Multiplatform, stable for iOS since 1.8.0 in May 2025 with improved SwiftUI/UIKit interop and a ~9 MB size overhead ([JetBrains](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)), or native SwiftUI over the same core. This is the one layout that fixes Fuelio's parity failure without paying for parity on day one. Both platforms compute identical numbers from identical code, and iOS can ship only when the parity matrix is green. The research positions KMP for long-lived apps and Android-first Kotlin teams, and Flutter for startups and custom UI ([Moodup](https://moodup.team/best-cross-platform-frameworks-in-2025-flutter-vs-react-native-vs-kotlin-multiplatform/); [Luciq](https://www.luciq.ai/blog/flutter-vs-kotlin-mutliplatform-guide)).

The alternatives lose for this brief. Flutter would give one UI codebase sooner and consistent RTL. But the features that matter most on Android are Glance widgets, Quick Settings Tiles, SMS and notification capture, Activity Recognition-driven trip detection, foreground services, Auto Backup and the Android for Cars App Library. All of them need native Kotlin under Flutter anyway ([Android Developers](https://developer.android.com/training/cars/apps)). Flutter also lacks first-party iOS 26 Liquid Glass ([The Flutter Kit](https://theflutterk.it.com/blog/flutter-liquid-glass-ios-26-cupertino-guide)). Choose Flutter instead only if the team is Dart-skilled and wants iOS within six months. Expo RN (SDK 55, New-Architecture-only, widgets still an iOS-only alpha) ([Expo](https://expo.dev/sdk/55)) suits a TypeScript team, but it is the weakest fit for Android-native integrations. Fully separate native apps double UI work. Compose and SwiftUI both mirror layouts for RTL automatically, which suits Arabic, though this claim is prior knowledge and not verified in this research.

| Layer | Android-first choice | iOS later |
|---|---|---|
| UI | Jetpack Compose, Material 3 (M3 Expressive optional) ([9to5Google](https://9to5google.com/2025/05/13/android-16-material-3-expressive-redesign/)); Vico charts (prior knowledge) | Compose Multiplatform or SwiftUI + Swift Charts |
| Shared core | KMP module: engine, importer, tariffs, sync client, SQLDelight or Room KMP schema | Same module, as an iOS framework |
| Local DB | SQLite via SQLDelight/Room (the notes list both for KMP) | Same |
| Sync | PowerSync Kotlin SDK (the notes list Kotlin among PowerSync's SDKs) over Postgres in AWS me-central-1 | PowerSync Swift/Kotlin |
| Quick capture | Glance home-screen widget, Quick Settings Tile, app shortcuts, Google Assistant App Actions | WidgetKit, Controls, App Intents, Live Activities |
| Backup | Auto Backup (`allowBackup` + `dataExtractionRules`, 25 MB per-app) + encrypted daily snapshot to Google Drive | iCloud Drive snapshot |
| OCR | ML Kit Text Recognition v2 on device ([ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2)) + proxied LLM fallback | Apple Vision / VisionKit |
| Car screen | Android for Cars App Library, POI category ([Android Developers](https://developer.android.com/training/cars/apps)) | CarPlay Fueling/EV entitlement ([Apple Forums](https://developer.apple.com/forums/thread/825705)) |
| Billing | Google Play Billing through RevenueCat (eases adding App Store later) | StoreKit via RevenueCat |
| Ops | Crashlytics or Sentry; PostHog; GitHub Actions + Fastlane / Gradle Play Publisher | Same, plus Xcode Cloud or Fastlane |

### Android capabilities to prioritise

**Widgets and fast entry.** A Glance widget shows "+ Fuel", this month's km/L and the days until Mulkiya expiry. A Quick Settings Tile and a long-press app shortcut open a prefilled form. The form pulls vehicle from the connected Bluetooth car, time from now, station from GPS and price from the official month. The user enters two of litres, AED and price/L, plus odometer. Target: at most 3 taps.

**SMS and notification capture.** The ecosystem research proposes parsing parking confirmations (7275/3009/5566) and Salik/Parkonic debits on device. Google Play restricts READ_SMS, so plan three tiers. First, the parking composer, which needs no permission: it opens the SMS app with `<plate> <zone> <hours>` prefilled and logs the known tariff. Second, a share-to-app flow for any SMS or receipt. Third, an opt-in automatic reader. Whether Play's SMS-permission exceptions cover expense tracking, and whether a `NotificationListenerService` reading SMS-app notifications is an acceptable alternative, are **prior knowledge, unverified**. Submit a policy pre-review before building it. The SMS Retriever API the notes mention is designed for one-time codes addressed to the app (prior knowledge). It cannot read third-party parking SMS, so do not rely on it. Exact SMS formats and sender IDs are still unknown, so collect real samples.

**Trip detection.** Use the Activity Recognition Transition API (IN_VEHICLE enter/exit) or a Bluetooth-car connect event to start a `location`-type foreground service with a visible notification. Use high-accuracy GPS only while driving. Android 12+ blocks starting location foreground services from the background, and Android 14 throws a SecurityException without background-location permission ([Android Developers](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start?authuser=2&hl=de); [Google](https://developers.google.com/maps/documentation/navigation/android-sdk/background-location-usage)). The flow must therefore request background location in context. It must also pass Play's background-location declaration, which needs a video. OEM battery killers (Xiaomi, Samsung) break tracking, the failure Fuelio's developer could not fully fix ([UserEcho](https://fuelio.userecho.com/users/1-adrian/comments/?page=5)). Ship per-OEM onboarding steps and the odometer-only fallback.

**Android Auto.** A pure logger may not qualify for any car-app category. The POI category covers gas and charging stations ([Android Developers](https://developer.android.com/training/cars/apps)) and is more attainable than CarPlay. Frame the car-screen app as "nearest station + this month's UAE price + one-tap log fill." Design guidance moved to the partner portal from 11 March 2026 ([Google](https://developers.google.com/cars/design/create-apps/app-types/other)). Fuelio already runs on Android Auto ([Ranger5G forum](https://www.ranger5g.com/forum/threads/fuelio-android-auto-apple-car-play-app-review-the-mpg-calculator-in-your-dash.22210/latest)), so this is parity, not a differentiator.

**Backups.** Auto Backup restores the database on a new phone for free. Add the encrypted Drive snapshot (last N versions, integrity check by test restore) and a first-launch restore prompt. Fuelio's Android Drive/Dropbox backups are file-based (`backup-sync`), so the importer can also read them directly from a user-picked Drive folder via the Storage Access Framework.

For data, **Realm Device Sync is dead (EOL 30 Sep 2025)** ([MongoDB](https://www.mongodb.com/docs/device-sync-deprecation/)). The 2026 default is on-device SQLite with PowerSync over Postgres. PowerSync is server-authoritative, not CRDT-based ([PowerSync](https://powersync.com/blog/electricsql-vs-powersync)), which fits append-mostly personal logs. Use row-level last-writer-wins with `updated_at` and tombstones, and never sync derived consumption. Supabase Pro costs about **$25/month** ([JetAdmin](https://www.jetadmin.io/blog/supabase-pricing-2026-guide-to-plans-limits-and-real-world-costs/)), but its Middle East region availability was not retrieved. For UAE residency, self-host Postgres and the PowerSync service on AWS me-central-1. Auth offers Google and email magic links at launch, and anonymous local-only use stays possible. Add Sign in with Apple with the iOS release, which Apple requires when other social logins are offered (prior knowledge). Skip UAE Pass: it is OIDC and integrable ([UAE PASS docs](https://docs.uaepass.ae/feature-guides/authentication/mobile-application/sdk-integration)), but private-sector onboarding is undocumented.

OCR is a two-tier pipeline. ML Kit v2 runs on the device for odometer and pump digits. When confidence is low, or for a full receipt, a backend-proxied Gemini Flash-class call with a JSON schema takes over, at about **$0.0003 per OCR task** in Roboflow's benchmark ([Roboflow](https://playground.roboflow.com/models/compare/gemini-2-5-flash-vs-gpt-4o)). GPT-4o-mini used about 111× the prompt tokens per image of Gemini in one 2024 eval ([Braintrust](https://www.braintrust.dev/evals/gemini)). Benchmark on 50–100 real ADNOC, ENOC and Emarat receipts in Arabic and English before choosing; Arabic accuracy is **unmeasured**. For ops, use PostHog for funnels (its free tier covers 100k errors and 5k recordings ([PostHog](https://posthog.com/blog/posthog-vs-sentry.md))) plus Crashlytics, which is free, or Sentry.

### Data model outline

```
user(id, locale, digits_pref, currency='AED', created_at)
vehicle(id, user_id, name, make, model, year, plate, emirate, vin?, fuel_grade_default,
        purchase_date, purchase_price?, active, updated_at, deleted_at)
energy_store(id, vehicle_id, kind[tank|battery], unit[L|kWh], capacity, usable_capacity,
             grade_default)                                       -- dual-fuel, PHEV
energy_event(id, vehicle_id, store_id, ts, odometer_km, amount, unit_price_aed, total_aed,
             is_full, missed_previous, level_before?, level_after?, soc_before?, soc_after?,
             grade, location_type[station|home|work|public_ac|public_dc|free|delivery],
             station_id?, tariff_id?, meter_source[wall|car], service_fee_aed, price_source
             [official_month|user|receipt_ocr], note, import_batch_id?, updated_at, deleted_at)
consumption_segment(vehicle_id, store_id, start_event_id, end_event_id, km, amount,
                    value, method[full_to_full|gauge|soc], sigma, valid)  -- LOCAL ONLY, derived
expense(id, vehicle_id, ts, odometer_km?, category_id, amount_aed, vat_aed?, is_income,
        source[manual|sms|email|ocr|import|composer], trip_id?, toll_gate_id?, parking_zone?,
        hours?, black_points?, original_amount_aed?, note, import_batch_id?, updated_at, deleted_at)
expense_category(id, user_id?, key[fuel|salik|darb|parking|mulkiya|inspection|insurance|
                 service|tyres|fine|wash|other], name_en, name_ar, color)
reminder(id, vehicle_id, kind, due_date?, due_odo?, repeat_months?, repeat_km?,
         lead_days, chained_to_id?, status)
document(id, vehicle_id, kind[mulkiya|insurance|warranty|receipt|licence], expiry?, file_ref)
trip(id, vehicle_id, start_ts, end_ts, start_odo, end_odo, km, purpose[private|work],
     polyline_ref?, fuel_cost_aed, toll_aed, parking_aed, source[gps|odo|plan])
route_plan(id, user_id, origin, dest, depart_ts, vehicle_id, km, est_total_aed,
           price_status[final|provisional|forecast], price_month)
station(id, brand[ADNOC|ENOC|Emarat|other], name, lat, lon, emirate)
-- server-published reference data (versioned, effective-dated, remote config) --
fuel_price(effective_month, grade[S98|SP95|EP91|DSL], aed_per_litre, announced_at,
           source_url, status[provisional|verified])
ev_tariff(id, network, charger_type[AC|DC], aed_per_kwh_incl_vat, idle_fee, effective_from)
toll_gate(id, operator[salik|darb], name_en, name_ar, lat, lon, bearing, linked_group?,
          effective_from, effective_to)
toll_schedule(operator, day_type[weekday|sunday|holiday|ramadan], start, end, aed, vat_incl,
              effective_from, effective_to)
parking_tariff(emirate, zone, space_type, day_type, start, end, aed_per_hour, sms_code,
               sms_surcharge, effective_from)
holiday_calendar(date, type, operator_scope)
import_batch(id, user_id, source[fuelio|drivvo|csv], file_name, stats_json, created_at, undone_at)
```

### Monetisation that answers Fuelio's paywall backlash

RevenueCat's 2026 data shows hard paywalls convert about 5× better than freemium by day 35 (**10.7% vs 2.1%** median). Trials of 17+ days convert at a 42.5% median, against 25.5% for trials of 4 days or less ([RevenueCat SOSA 2026](https://www.revenuecat.com/state-of-subscription-apps-2026/); [RevenueCat trial length](https://www.revenuecat.com/blog/growth/free-trial-length)). A hard paywall would still be self-defeating here. Fuelio and Drivvo are free at the core, Drivvo costs about €6/year in Italy ([Motor1](https://it.motor1.com/features/392300/drivvo-app-gestione-costi-auto/)), and the anger toward Fuelio is specifically about paywalls.

The recommendation, which is inference and not sourced willingness-to-pay data, has four parts. The free tier covers full logging for up to 2 vehicles, correct consumption, expenses, UAE price auto-fill, Mulkiya and insurance reminders, Fuelio import, CSV export and local backup. The research notes disagree here: one suggests 1 free vehicle, the other ≥2. Two vehicles fits the common UAE household. **Plus** costs about AED 9.99/month or AED 59.99–79.99/year with a 14–21-day trial; test 14 against 21 days, since the 17+-day bucket converts best. Plus adds cloud sync, unlimited vehicles, AI receipt scans, trip detection, business mileage reports, the full route estimator with saved-trip re-pricing, family sharing and PDF history. A **lifetime** unlock costs AED 149–199, with AI scans metered because they carry marginal cost. **Fleet** is a later per-vehicle subscription. For reference, Fuelio's US$17.99 is about AED 66 at the fixed peg. Never paywall read access or export, and grandfather features users already had for free. Use RevenueCat for cross-platform entitlements. Do not show ads in the core flow, since Drivvo's ads are called intrusive ([Auto Express](https://www.autoexpress.co.uk/accessories-tyres/92535/fillup-app-review)).

## In-car Android head units: detecting refuels automatically

A car can tell the app that fuel went in and roughly how much. It cannot tell the app what the fill cost. On Android there are four routes to the fuel level, and they differ sharply in data, permissions and how many UAE cars they actually reach. iOS CarPlay was out of scope for this research. Several hosts were blocked during it (Wikipedia, python-obd docs, the PLEOS developer site, CSS Electronics), so some OBD and OEM facts below come from search snippets and mirrors and are flagged.

On **Android Automotive OS** (Google built-in), the platform `CarPropertyManager` exposes nearly everything refuel detection needs ([VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)). `FUEL_LEVEL` is reported in **millilitres, not percent**, so litres added is a direct difference with no tank-capacity multiplication. `FUEL_DOOR_OPEN` is an on-change Boolean behind the *normal* permission `CAR_ENERGY_PORTS`, so it needs no user prompt. `IGNITION_STATE` and `PARKING_BRAKE_ON` sit behind the normal `CAR_POWERTRAIN`, and `INFO_FUEL_CAPACITY` behind the normal `CAR_INFO`. `FUEL_LEVEL`, `RANGE_REMAINING` and `FUEL_LEVEL_LOW` need the dangerous `CAR_ENERGY`, speed needs the dangerous `CAR_SPEED`, and `PERF_ODOMETER` needs the dangerous `CAR_MILEAGE_3P`. The older `CAR_MILEAGE` and `CAR_IDENTIFICATION` (VIN) are signature|privileged and unavailable to Play apps ([android.car.Car](https://developer.android.com/reference/android/car/Car); [Medium](https://medium.com/@sreelakshmis.dilip/android-automotive-understanding-app-permissions-and-privileged-applications-c00b83df11d6)). The platform allows this, but two other gates decide real access. The OEM must implement each property in its Vehicle HAL, and OEMs may restrict third-party access further. An unofficial guide lists `FUEL_LEVEL` as open to third parties ([akshay-2 gitbook](https://akshay-2.gitbook.io/android_automotive/hardware-capabilities-in-aaos)), while Hyundai's AAOS-based PLEOS guide reportedly marks it **not available** to them ([PLEOS](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability); search snippet only, host blocked, **unverified**).

On **Android Auto** (phone projection), the Jetpack Car App Library's `CarInfo` gives `EnergyLevel` (fuel percent, battery percent, range, low-fuel flag), `Mileage` (odometer), `Speed`, `EnergyProfile` and `Model`. It requires Car App API level 3 and the GMS permissions `CAR_FUEL`, `CAR_MILEAGE` and `CAR_SPEED` ([CarInfo](https://developer.android.com/reference/androidx/car/app/hardware/info/CarInfo); [Car Hardware APIs](https://developer.android.com/training/cars/apps/library/car-hardware-api)). Fuel comes **only as a percentage**, so litres = Δ% × the tank capacity the user enters. There is **no fuel-door signal**. Listeners run only while the app's templated session is projected, and Google warns "Don't assume that the car's data is available at all times… check the status" ([Car Hardware APIs](https://developer.android.com/training/cars/apps/library/car-hardware-api)). A community answer adds that "not all vehicles support all of the fields" ([Stack Overflow mirror](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/76950855/android-auto-getting-data-about-car)). One oddity matters for the AAOS build: odometer via `CarInfo` is "not available to Android Automotive OS apps installed from Google Play," so an AAOS build must read `PERF_ODOMETER` directly.

Both car-screen routes share a distribution constraint. Google Play lists a car app only if it fits a supported category ([Android for Cars App Library](https://developer.android.com/training/cars/apps)). A fuel logger is not a category. **POI** is, on both Android Auto and AAOS, and a "fuel stations near me" app fits it ([Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)). Car-optimised AAOS apps must deliver driving functionality through templates, not their own activities, except for setup, settings and sign-in while parked ([Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)). This matches the POI framing already proposed for Android Auto above.

**Aftermarket Chinese head units** (Joying, ATOTO, Teyes, Dasaita on FYT or MTCD platforms) are common retrofits, but they have **no documented, stable public API** for vehicle data. The unit does not read CAN itself. A car-specific decoder box (Raise/RZC, Hiworld, Simple Soft, LZ) translates CAN to UART, and a vendor system app parses it ([ih8mud forum](https://forum.ih8mud.com/threads/looking-for-a-picture-of-the-lx470-climate-control-screen.1305009/post-14826155)). On FYT units that app is `com.syu.canbus`. The FYT Launcher Mod reads it through reverse-engineered `DataCanbus.DATA[...]` codes, for example `[102]` fuel consumption and `[103]` range ([FYT Launcher Mod](https://github.com/vasyl91/FYT-Launcher-Mod)). The same project reports that codes vary by car and firmware, a wrong code reads 0, some units provide nothing, consumption disappears below about 30–40 km/h, and **fuel level and odometer are not covered**. Android 8+ also stops delivering some FYT system broadcasts, such as ACC-on, to non-system apps ([FytHWOneKey](https://www.github.com/hvdwolf/FytHWOneKey)). The useful fact is the opposite one: the head unit is a full Android tablet with no car-category restrictions, so the normal phone app can run on it and talk to an OBD dongle itself.

An **OBD-II dongle** (ELM327 clone or OBDLink) is the only route that reaches most cars regardless of head unit. The relevant Mode 01 PIDs are `0x2F` fuel tank level (A×100/255 %), `0x0D` speed, `0x5E` engine fuel rate ((256A+B)/20 L/h), `0x10` MAF as a fuel-flow fallback, and `0xA6` odometer (4 bytes, 0.1 km) ([Scribd J1979 table](https://www.scribd.com/document/399698761/OBD-II-PIDs-pdf); [OBD2 PIDs header](https://git.supported.systems/warehack.ing/i-k-bus-board/src/branch/main/firmware/lib/KLine/Obd2Pids.h); [Scribd OBD doc](https://www.scribd.com/document/809757342/OBD)). The primary PID table could not be fetched, and the `0xA6` formula is inferred from its maximum value. Support is patchy in practice. A 2018 VW Passat answered neither `0x2F` nor `0xA6`, although `0xA6` was in its support bitmap ([AutoPi community](https://community.autopi.io/t/vw-passat-2018-read-out-fuel-level-and-odometer-value-not-possible/3286)), and an F-150 gave no fuel level ([ford-trucks.com](https://www.ford-trucks.com/forums/1674086-fuel-level-pid.html)). Fuel rate is not mandatory in the standard ([arXiv 1611.09065](https://arxiv.org/pdf/1611.09065)). Kotlin and Java libraries exist ([barnhill/androidobd](https://awesome.ecosyste.ms/projects/github.com%2Fbarnhill%2Fandroidobd); [obd-java-api](https://github.com/designsome/obd-java-api)). OBD Fusion and Torque log fuel economy, but **no app found advertises automatic refuel detection** ([OBD Fusion](https://apps.apple.com/gb/app/obd-fusion/id650684932?uo=2); [toyotaownersclub](https://www.toyotaownersclub.com/forums/topic/141470-torque-on-android-sensor-readings/page/2/)).

| Route | Data available | API | Permissions | Play category constraints | Real-world coverage |
|---|---|---|---|---|---|
| AAOS built-in | Fuel level in mL, fuel-door open, tank capacity, ignition, parking brake, speed, odometer, range, low-fuel flag; EV SoC and charge-port state | `CarPropertyManager` + `VehiclePropertyIds` ([ref](https://developer.android.com/reference/android/car/VehiclePropertyIds)) | Normal: `CAR_ENERGY_PORTS`, `CAR_POWERTRAIN`, `CAR_INFO`. Dangerous: `CAR_ENERGY`, `CAR_SPEED`, `CAR_MILEAGE_3P`. VIN is privileged-only ([Car](https://developer.android.com/reference/android/car/Car)) | Must be a supported category (POI fits); driving UI templated. Whether a POI app may keep a property callback alive in the background is **undocumented** | Only cars with Google built-in, and only where the OEM implements and exposes each property. PLEOS reportedly blocks `FUEL_LEVEL` (**unverified**). No OEM-by-OEM list found |
| Android Auto | Fuel %, battery %, range, low-fuel flag, odometer, speed, make/model/year. **No fuel door** | Car App Library `CarInfo` (API level 3+) ([ref](https://developer.android.com/reference/androidx/car/app/hardware/info/CarInfo)) | `com.google.android.gms.permission.CAR_FUEL`, `CAR_MILEAGE`, `CAR_SPEED`, granted by the user | POI, navigation, IoT or weather; listeners live only while the projected session runs | Widest car-screen reach in the UAE (inference), but fields are often unpopulated; no public per-model list; callback rate and % granularity unknown |
| Aftermarket FYT/Joying + CAN box | Usually consumption and range; fuel level and odometer typically **not** exposed | Reverse-engineered `com.syu.canbus` `DataCanbus` codes; no public SDK ([FYT Launcher Mod](https://github.com/vasyl91/FYT-Launcher-Mod)) | Accessibility Service for live stats; CAN features off on AOSP-test-key ROMs | None: the unit is a plain Android tablet, so sideload or Play install works | Per firmware family and per car profile; whether common UAE models (Land Cruiser, Patrol, Camry, Hilux) expose fuel level is **unknown** |
| OBD-II dongle | Fuel level % (`0x2F`), speed, fuel rate (`0x5E`) or MAF, odometer (`0xA6`) where supported | ELM327 AT commands over BT classic SPP, BLE GATT or Wi-Fi; Kotlin/Java libraries | Android Bluetooth (and location for scanning, prior knowledge) on the phone or head unit | None beyond normal phone-app rules | Connects to most post-2008 petrol cars via ISO 15765 CAN (inference), but `0x2F`/`0xA6` are often missing; Toyota and Nissan may need Mode 22 PIDs (**unverified**). |

Manufacturer cloud APIs (Smartcar, High Mobility for BMW and Mercedes, Volvo) also expose fuel level and odometer, but their published coverage is **North America and Europe only** ([Smartcar](https://smartcar.com/product/fuel); [High Mobility BMW](https://docs.high-mobility.com/oem-guides/bmw-and-mini)). Tesla routes the Middle East to its EU host ([Tesla base URLs](https://developer.tesla.com/docs/fleet-api/getting-started/base-urls)), which matters little for fuel. Leave them out of the plan until UAE coverage is confirmed.

### Detection algorithm: event trigger plus stable before/after levels

The notes propose the algorithm below. It is an engineering design, not one taken from a source. It runs in the shared KMP core so every route feeds the same logic.

```
state: L_prev, odo_prev, t_prev                       # last STABLE reading, persisted across sessions

stable_reading():                                     # median filtering while stationary
  require speed == 0 and (gear == P or parking_brake_on)
  return median(N samples over ≥ 10 s)                # rejects slosh; level ground preferred

triggers (any):
  a. AAOS FUEL_DOOR_OPEN true → false                 # best signal, normal permission, AAOS only
  b. IGNITION_STATE OFF/ACC → ON, or an Android Auto / OBD session reconnect
  c. speed == 0 for > 2 min inside a fuel-station geofence (phone location + station table)
  corroborating: FUEL_LEVEL_LOW clears ("should not be cleared until more fuel is added")

on trigger:
  wait 30–120 s after ignition-on                     # many clusters re-sample the sender at start-up
  L_new, odo_new = stable_reading()
  ΔL = L_new − L_prev
  accept if ΔL ≥ max(5 L, 0.08 × capacity) and |odo_new − odo_prev| < 1–2 km
  litres = AAOS: ΔmL / 1000
           Android Auto / OBD: Δ% / 100 × capacity    # capacity: INFO_FUEL_CAPACITY, user, or model lookup
  litres = min(litres, capacity − L_prev)
  likely_full = L_new ≥ 0.97 × capacity
  after 20–50 km: re-estimate L_new from the filtered trend; optionally reconcile with ∫ PID 5E / MAF
  price: litres × fuel_price[grade][month(ts)]        # UAE official monthly price, never from the car
```

The `FUEL_LEVEL_LOW` hint and the change modes (`FUEL_DOOR_OPEN` on-change, `FUEL_LEVEL` continuous) come from the [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds) reference. The stationary filter follows from slosh noise: fuel readings are reliable mainly when stopped on level ground ([MoTeC forum](https://forum.motec.com.au/viewtopic.php?p=14460)). On Android Auto the session limit shapes the design. A refuel with the phone disconnected is caught only on the next connection, as a step against the last stored value, so `L_prev` must survive across sessions.

**Price never comes from the car.** No route exposes what the fill cost. The app multiplies litres by the official UAE monthly price for the user's grade (Super 98, Special 95, E-Plus 91 or diesel), the same `fuel_price` table that drives the add-fill form and route estimator. If an ADNOC or ENOC digital receipt or a payment SMS arrives, its actual total replaces the estimate.

**Error caveats.** Expect about **±5–10 % of tank capacity**. That figure is an engineering estimate with no primary source, and it needs field validation. Gauge senders are often float or resistive types with deliberately damped output. Tank geometry is non-linear: 4x4 saddle tanks are common in the UAE fleet, and the top of the gauge often reads "full" for several litres. On some GM trucks the displayed percentage is not computed directly from the sender ([tahoeyukonforum](https://tahoeyukonforum.com/threads/wacky-fuel-gauge.55319/page-2)). Quantisation is minor by comparison: 1 % (0.4 % for OBD byte scaling) on a 70–93 L tank is about 0.7–0.9 L. The likely-full flag therefore matters more than the litre estimate. Full-to-full consumption math (formula A) stays exact as long as the user confirms the full tank and the pump litres, because it never uses gauge levels. Detected-only volumes count as gauge estimates (formula D) and show as "~".

### Recommendations, Android-first

1. **Phase 3: Android Auto POI app with `CarInfo` detection.** Ship the "nearest station + this month's UAE price + one-tap log" app already planned, and request `CAR_FUEL` and `CAR_MILEAGE` in it. Detect refuels from `fuelPercent` steps within a session and across sessions, using the ignition/reconnect and station-geofence triggers. POI is the category that justifies both the car screen and the permission. Whether Play review accepts a POI app that also auto-logs refuels is **unconfirmed**, so raise it in the Phase 0 policy pre-check.
2. **AAOS build later (Phase 5).** Reuse the same POI app as an `app-automotive` build and read `CarPropertyManager` directly for `FUEL_LEVEL` in mL, `FUEL_DOOR_OPEN`, `IGNITION_STATE` and `PERF_ODOMETER`. Google built-in cars are few in the UAE today (inference), and OEM exposure is unconfirmed, so this follows the Android Auto release.
3. **OBD as the universal fallback (Phase 5).** Add optional ELM327/OBDLink support for cars without a usable car-screen signal, including aftermarket head units, where the phone app installs directly. Probe the PID-support bitmaps per car and fall back silently when `0x2F` is absent. Keep live OBD data free, given FIXD's backlash noted above. Treat FYT `com.syu.canbus` codes as an experimental extra, not a supported route.
4. **Prefer fuel-door and ignition signals where available.** Use the AAOS fuel-door edge first, then an ignition cycle or session reconnect, and the stationary-at-station geofence last. A level jump alone, with no event, should only raise a "Did you refuel?" prompt.
5. **Detection prefills the add-fill draft; it never saves silently.** A notification or car-screen prompt opens the normal add-fill form already filled in: vehicle, timestamp, station from geofence, odometer from the car, estimated litres marked "~", the full-tank toggle preset from likely-full, and price/L and AED total from the official month. The user confirms or corrects the pump litres and saves in one tap, which keeps the ≤3-tap target. The saved row keeps `level_before`/`level_after` from the car, and `price_source` stays `official_month` until a receipt overrides it. Rejected or dismissed drafts are discarded, so false positives never touch the consumption engine.

### Gaps

- No public list of vehicle models that populate `fuelPercent` or `Mileage` on Android Auto, and no data on energy-level callback rate or percentage granularity.
- No source on which OEMs implement or whitelist `FUEL_DOOR_OPEN` and `FUEL_LEVEL` for third-party AAOS apps. The PLEOS restriction is snippet-only.
- No source on the API level that introduced `CAR_MILEAGE_3P`.
- Undocumented whether a Play-installed AAOS POI app may keep a `CarPropertyManager` callback alive in the background, and whether Play review accepts automatic refuel logging in a POI app.
- No documented Teyes SDK, canbox UART protocol or MTCD fuel intents, and no confirmation that `com.syu.canbus` exposes fuel level for common UAE models.
- No UAE-specific OBD compatibility data, and no source for per-make Mode 22 fuel-level PIDs. The primary PID tables were blocked.
- The ±5–10 % gauge-error figure is unsourced. Validate it with field tests on 5–10 common UAE cars, comparing detected litres with pump litres.
- UAE or GCC coverage for Smartcar, High Mobility, BMW, Mercedes and Toyota/Lexus cloud APIs is unconfirmed.

## Six phases take the app from Android MVP to iOS and fleet in about 16 months

The timelines and targets below are planning estimates for 2–3 Android/Kotlin engineers plus a designer. They are not benchmarks from the research, except where a RevenueCat figure is cited. The sequence is Android-first. UAE reminders and tolls sit in the MVP because they are the core differentiators. Android-native capture features (widget, tile, backup, SMS, trips, Android Auto) arrive before iOS. iOS reuses the KMP core and ships only at feature parity.

| Phase | Timing (est.) | Features | Exit / success metrics (targets) |
|---|---|---|---|
| 0. Foundations | Weeks 1–4 | Verify the price table against WAM and back-fill history to 2015. Build the price-ingestion job and admin screen. Map Salik/Darb gate coordinates. Collect 20+ real Fuelio backups, 50–100 UAE receipts and parking/Salik SMS samples. Set up the KMP module skeleton and the Compose design system (Arabic/English RTL, dark mode, semantic tokens). PDPL consent review. Play policy pre-check for SMS, background location and refuel auto-logging in a POI car app. | Price ingestion runs on 2 consecutive month-ends with zero manual fixes. Importer passes every collected file. |
| 1. Android MVP (local-only) | Months 2–4 | Vehicles and energy stores. Fill-up in ≤3 taps with auto-filled monthly price. Correct full-to-full/missed/partial engine with explanations. Edit everything. Expenses with UAE presets. Mulkiya reminder chain with notifications. Fuelio CSV/zip importer (file picker and Drive folder via SAF) with preview and undo. CSV export. **Auto Backup + encrypted Drive snapshot** with status. **Glance widget, Quick Settings Tile, app shortcuts.** Parking SMS composer. Charts: km/L, cost/km, monthly spend, price-change impact. | Crash-free sessions ≥99.5%. ≥60% of new users log a first fill within 10 min. ≥25% of early users arrive via Fuelio import. Play rating ≥4.5. Imported averages match hand calculations. |
| 2. Android v1.5 Sync, capture, revenue | Months 5–7 | Accounts (Google, magic link) and PowerSync on UAE-region Postgres. ML Kit receipt/pump/odometer OCR with LLM fallback. Play Billing via RevenueCat (monthly, annual, lifetime). Salik/Darb tariff engine with Ramadan and holiday calendars. **Opt-in SMS/notification capture** for parking and Salik (on-device parsing; share-to-app fallback). Email-forward receipts. EV/PHEV charging with UAE tariffs and η. Document vault. Monthly price push. Drivvo/generic CSV import. | Download-to-paid ≥2% by day 35 (the freemium median is 2.1%). Trial-to-paid ≥35%. OCR fields accepted unedited ≥85%. ≥70% of captured SMS parsed correctly. D30 retention ≥25%. |
| 3. Android v2 Trips, route and car screen | Months 8–11 | **Trip detection** (Activity Recognition or Bluetooth-car trigger, location foreground service, per-OEM battery guidance, odometer fallback). UAE route cost estimator (month-aware price, provisional band, re-price on announcement, Salik/Darb via polyline, parking, EV mode, passenger split). Saved commutes with peak-avoidance savings. Business mileage PDF. **Android Auto POI app** (nearest station, month price, one-tap log) with **`CarInfo` refuel detection** that prefills the add-fill draft (fuel-% step, ignition/reconnect and station-geofence triggers, litres × official month price). Family/shared vehicles. Wear OS tile quick-log. | Estimator within ±10% of logged cost on test commutes. Trip-detection battery complaints <1% of reviews. Android Auto approval obtained. Detected refuels within ±10% of pump litres on supported test cars, with false-positive drafts <5% (targets). Paid churn ≤5%/month. |
| 4. iOS launch at parity | Months 11–14 | iOS app on the same KMP core (Compose Multiplatform or SwiftUI). WidgetKit, Controls and App Intents; iCloud snapshot; Sign in with Apple; StoreKit via RevenueCat; Fuelio import (validate iOS export files first, since their format is unverified). No SMS reading on iOS; offer share-sheet and Shortcuts capture instead. Publish a public parity matrix. | Feature parity matrix ≥95% (except SMS). Identical consumption results on shared test fixtures. App Store rating ≥4.5. |
| 5. Fully fledged | Months 14–16+ | Small-fleet tier (drivers, per-driver Salik/fines, accounting exports). Service schedules for top UAE models. Resale history PDF. Community benchmarks (opt-in). Web dashboard. **AAOS build** of the POI app reading `CarPropertyManager` (`FUEL_LEVEL` in mL, `FUEL_DOOR_OPEN`, ignition, odometer) for refuel detection. Optional OBD-II without paywalled live data, as the universal refuel-detection fallback (including aftermarket head units). CarPlay Fueling/EV application (approval uncertain). Optional fuel-price forecast labelled as an estimate. | ≥50 paying fleet accounts. Revenue covers infrastructure plus one engineer. Ratings hold ≥4.6. |

Instrument the funnel from day one with PostHog: install → vehicle created → first fill → second full fill (the first consumption number) → 30-day retained → trial → paid. The second-full-fill event is the real "aha" moment, because no consumption exists until then. Onboarding should therefore offer import, or a gauge-based first estimate, to shorten that wait.

## Conclusion

The research changes the framing from "a nicer Fuelio" to "the UAE's car-cost ledger." Fuel economy is a commodity metric that every logger computes, and most compute it slightly wrong. What no app combines is an exact, month-effective national price; tolls and parking that rival fuel spend; and government renewal deadlines that carry fines. Fuelio's flagship Pro feature, stations on route, is close to worthless in a market with one price per grade. Its weakest point, data that dies with the phone, is the easiest to beat with SQLite-first sync and Android Auto Backup. On Android, the SMS confirmations UAE parking already sends are a capture channel that no global competitor exploits, though Play policy must be cleared first. The 2026 price swings make month-aware estimation more than a nicety. A one-month AED 1.97 diesel jump turns any "current price" estimator into a source of wrong answers. Re-pricing saved trips on announcement night becomes a retention hook that crowdsourced apps structurally cannot copy.

The main risk is not technology but tariff drift. Salik's VAT start date, Darb's caps, Darb's gate list and Dubai's 2026 parking rates all conflict across sources within a single year. The durable moat is therefore an operations capability as much as an app: versioned, effective-dated tariff tables, a two-source verification job for prices, and a fast admin override. The tolerant Fuelio importer is the cheapest growth lever, because switchers bring years of history and immediately see a corrected average. Build both before polishing anything else.
