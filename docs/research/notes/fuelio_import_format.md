# Fuelio Data Export / Backup Format (for building a Fuelio importer)

Research note scope: Fuelio CSV backup structure, how users get the file out of the app, and how existing open-source tools parse it. Primary evidence is real sample files and parser source code on GitHub (fetched via raw.githubusercontent.com). The official Fuelio FAQ (fuel.io) and LubeLogger docs (docs.lubelogger.com) could not be fetched from this session (hosts blocked); their content is cited only via search-result snippets.

## What sections does a Fuelio CSV contain, and what are the exact column headers?

### Takeaway
A Fuelio backup is ONE vehicle per file (`vehicle-<N>-sync.csv`, usually inside a `.zip`), split into sections introduced by a single-cell marker row `"## <Name>"`, each followed by its own header row. Observed sections, in order: `## Vehicle`, `## Log`, `## CostCategories`, `## Costs`, `## FavStations`, `## Category`, plus `## TripLog` in newer backups. Unit-dependent header names (e.g. `Odo (km)` vs `Odo (mi)`) mean an importer must match headers by prefix/position, not exact string.

### Cited Findings

**Real sample #1 (2024 backup, miles, UK car)** — full file `fuelio_backup_sample.csv` — [tigattack/lubelogger-fuelio-importer sample](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv):

```
"## Vehicle"
"Name","Description","DistUnit","FuelUnit","ConsumptionUnit","ImportCSVDateFormat","VIN","Insurance","Plate","Make","Model","Year","TankCount","Tank1Type","Tank2Type","Active","Tank1Capacity","Tank2Capacity","FuelUnitTank2","FuelConsumptionTank2"
"BMW Z3","","1","0","2","yyyy-MM-dd","","","AB12CDE","BMW","Z3","1997","1","100","0","1","51.0","0.0","0","0"
"## Log"
"Data","Odo (mi)","Fuel (litres)","Full","Price (optional)","mpg (optional)","latitude (optional)","longitude (optional)","City (optional)","Notes (optional)","Missed","TankNumber","FuelType","VolumePrice","StationID (optional)","ExcludeDistance","UniqueId","TankCalc"
"2024-03-10 16:01","212477.0","46.301","1","67.09","21.8","51.16514","-2.99017","Somerset - Sun all Service Station","","0","1","-1","1.449","496456","0.0","218","0.0"
"## CostCategories"
"CostTypeID","Name","priority","color"
"1","Service","0",""
"2","Maintenance","0",""
"4","Registration","0",
"5","Parking","0",""
...
"31","Insurance","0",""
"32","Accessories","0","#424242"
"33","Other","0","#424242"
"34","MOT","0","#424242"
"35","Tax","0","#424242"
"## Costs"
"CostTitle","Date","Odo","CostTypeID","Notes","Cost","flag","idR","read","RemindOdo","RemindDate","isTemplate","RepeatOdo","RepeatMonths","isIncome","UniqueId"
"MOT","2024-02-08 14:57","0","34","","0.0","0","0","0","0","2024-07-19","0","0","0","0","51"
"## FavStations"
"NameBrand","Latitude","Longitude","StationID","Description","CountryCode"
"Morrisons","51.16514","-2.99017","208772","Somerset - Sun all Service Station","GBR"
"## Category"
"IdCategory","Name"
"1","Private"
"2","Work"
```
(Note: the `"4","Registration","0",` row has an unquoted empty last field — the file is not perfectly uniform.)

**Real sample #2 (2018 backup, km/litres, Czech car)** — [dundee/fuelio2fuelly example-fuelio.csv](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv):

```
"## Vehicle"
"Name","Description","DistUnit","FuelUnit","ConsumptionUnit","ImportCSVDateFormat","VIN","Insurance","Plate","Make","Model","Year","TankCount","Tank1Type","Tank2Type","Active","Tank1Capacity","Tank2Capacity"
"Toyota Auris","","0","0","0","yyyy-MM-dd","","","","toyota","auris","2018","1","100","0","1","50.0","0.0"
"## Log"
"Data","Odo (km)","Fuel (litres)","Full","Price (optional)","l/100km (optional)","latitude (optional)","longitude (optional)","City (optional)","Notes (optional)","Missed","TankNumber","FuelType","VolumePrice","StationID (optional)","ExcludeDistance","UniqueId","TankCalc"
"2018-10-07","424","33.04","1","1172.92","0.0","50.0436","14.4406","Praha, Olbrachtova - Mol","","0","1","0","35.5","329285","0","105","0.0"
"2018-09-17","5","48.02","1","1675.9","0.0","50.0165","14.4026","Hlavní město Praha, Československého exilu - OMV","","0","1","0","34.9","107548","0","103","0.0"
```

- Header evolution: the 2018 Vehicle header has 18 columns; the 2024 one adds `FuelUnitTank2`, `FuelConsumptionTank2` (20 columns). tigattack's model also lists optional `guid` and `lastupdated` Vehicle columns seen in some backups — [flio_models.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/flio_models.py).
- Log header is 18 columns in both samples; the Log rows are ordered newest-first in sample #2 — [example-fuelio.csv](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv).
- Unit-dependent Log header names observed: `Odo (km)` / `Odo (mi)`, `l/100km (optional)` / `mpg (optional)`; `Fuel (litres)` in both — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv). fuelio2fuelly detects miles by checking `line[1] == 'Odo (mi)'` — [convert.py](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/convert.py).
- Older Fuelio-import format (what Fuelio accepts as input, written by the converter by Fuelio's own ecosystem "Programistyk"/Kamil Kamiński): marker rows padded with commas (`## Vehicle,,,,,,,,,,,,,,`), no spaces in headers (`Odo(km)`, `Fuel(litres)`, `Price(optional)`, `l/100km(optional)`...), Vehicle header only 16 cols (ends at `Active`), Log header 14 cols (ends at `VolumePrice`), CostCategories `CostTypeID,Name,priority`, Costs header identical to modern one — [fueliobackupbuilder.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/fueliobackupbuilder.class.php). Implies Fuelio's own importer tolerates missing trailing columns.
- `## TripLog` section exists in newer backups. Columns referenced by the HA-Fuelio parser: `StartDate`, `EndDate`, `StartOdo`, `EndOdo`, `TripDist`, `TripDuration`, `TripCost`, `TripNote`, `title`/`Title`; it also notes the section contains coordinates/route geometry and internal IDs — [HA-Fuelio parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py). HA-Fuelio requires `Vehicle`, `Log`, `Costs`, `TripLog` sections; `CostCategories` optional — same source.
- tigattack's README says the single CSV contains `## Vehicle`, `## Log`, `## CostCategories` "etc.", each with its own header row, and that `csv.DictReader` breaks because it treats the `## Vehicle` line as the file header — [tigattack/lubelogger-fuelio-importer README](https://github.com/tigattack/lubelogger-fuelio-importer).

### Inferences
- Robust parse strategy: read with a real CSV reader; a row whose single (or first non-empty) cell starts with `## ` begins a new section (strip quotes and trailing commas); the next row is the header; then data rows until the next marker. Do not assume a fixed column count; map by header name.
- Normalize header names by stripping spaces and the `(optional)` suffix and the unit parenthetical (`Odo (mi)` -> `Odo` + unit `mi`), so both old (`Odo(km)`) and new (`Odo (km)`) variants match.
- Full TripLog header order is unconfirmed (see Gaps); treat as optional.

### Gaps
- No official, published Fuelio CSV spec found; fuel.io FAQ could not be fetched (host blocked). Exact TripLog column list/order and any additional sections (e.g. reminders, tyres, EV charging extras) unverified.
- No sample with `TankCount=2` / dual-tank data found.
- Gallons header text (e.g. `Fuel (gal)`?) not observed in any sample.

## Field meanings, unit codes and fuel type codes

### Takeaway
`Price` in Log is the TOTAL cost of the fill-up and `VolumePrice` is price per unit (46.301 x 1.449 = 67.09; 33.04 x 35.5 = 1172.92). `Full`, `Missed`, `Active` are 0/1 flags. Units in the Vehicle row are integer codes; FuelType is a hierarchical numeric code (100s gasoline, 200s diesel, 300s ethanol, 400 LPG, 500 CNG, 600 electricity, 700 flex). There is no city/highway percentage field in any observed Log header.

### Cited Findings
- Log fields (from the converter's model, author Kamil Kamiński): `Data` = fill-up timestamp; `Odo` = total odometer reading; `Fuel` = amount of fuel; `Full` = 0|1 full vs partial fill-up; `Price` = "Total cost of fueling"; consumption column = "Fuel consuption, calculated by Fuelio"; `latitude`/`longitude` = geo; `City` = "Geo city name, required to display on map" (the converter writes `GPS` if coords exist without a city); `Notes`; `Missed` = 0|1 "there are missing fueling before this entry"; `TankNumber` = tank (default 1); `FuelType` = id from FuelType table; `VolumePrice` = volume (unit) price — [fuellogentry.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/fuellogentry.class.php).
- In real exports `City (optional)` actually holds a station/place label, e.g. "Somerset - Sun all Service Station", "Praha, Olbrachtova - Mol" — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv). tigattack maps it to "station" — [flio_models.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/flio_models.py).
- The consumption column can be `0.0` (not computed) — [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv); in sample #1 it is `21.8` mpg — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv).
- `StationID (optional)` links to `FavStations.StationID`; `UniqueId` is a per-record id; `ExcludeDistance` appears as `0` or `0.0`; `TankCalc` `0.0` (meaning undocumented) — same samples. HA-Fuelio treats `ExcludeDistance` not in {"", "0", "0.0"} as true and excludes `Missed`/`ExcludeDistance` windows from measured economy; treats `Missed` in {"1","1.0"} as true — [HA-Fuelio parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).
- Vehicle unit codes: `DistUnit` 0=kilometers, 1=miles; `FuelUnit` 0=litres, 1=US gallons, 2=UK gallons; `ConsumptionUnit` 0=l/100km, 1=MPG US, 2=MPG UK, 3=km/l, 4=km/gal US, 5=km/gal UK — [vehicle.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/vehicle.class.php). Consistent with sample #1 (DistUnit=1 miles, ConsumptionUnit=2, header "mpg") — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv).
- Note sample #1 has DistUnit=1 (miles) but FuelUnit=0 and header `Fuel (litres)` — UK users mix miles + litres — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv).
- Fuel type table (`Id,ParentId,Name`): roots 100 Gasoline, 200 Diesel, 300 Ethanol, 400 LPG/GPL, 500 CNG, 600 Electricity, 700 Flex. Children e.g. 110 "95", 111 "95+", 112 "98", 114 "100", 201 "ON", 202 "ON+", 206 ULSD, 209 B20, 211 B100, 216 DEF, 217 AdBlue, 302 E10, 305 E85, 401 GPL/LPG, 501 CNG, 601 AC 240V, 602 DC 500 Fast Charge — [FuelType.csv](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/FuelType.csv).
- tigattack uses a different, friendlier label map (110 "Petrol Regular", 112 "Petrol Super", 113 "Petrol Ultimate", 114 "Petrol Racing", 119 "Petrol E10", 201 "Diesel Regular", 202 "Diesel Plus", 209 "Biodiesel B20", 211 "Biodiesel", 217 "Diesel Adblue", 218 "Biodiesel B10", 219 "Biodiesel B30", 305 E85, 401 LPG, 501 CNG, 502 CBG, 503 Biogas, 601 240V, 602 DC 500 Fast Charge), plus 0 = "Unset" and -1 = "Unknown" (sample #1 row has FuelType `-1`) — [fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py). Codes 119, 218, 219, 502, 503 are absent from the 2018 FuelType.csv, so the list has grown — [FuelType.csv](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/FuelType.csv).
- `Tank1Type` = `100` in both samples, i.e. a root fuel-family id (Gasoline); `Tank2Type` = `0` when single tank; `TankCount` = 1 — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv).
- Costs: `CostTypeID` joins to `CostCategories.CostTypeID`; `isTemplate=1` and `isIncome=1` rows should be excluded from booked expenses; `RemindOdo`/`RemindDate`/`RepeatOdo`/`RepeatMonths` are reminder/recurrence fields — [HA-Fuelio README](https://github.com/Jocke1970/HA-Fuelio), [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py). Default Fuelio category ids seen: 1 Service, 2 Maintenance, 4 Registration, 5 Parking, 6 Wash, 7 Tolls, 8 Tickets/Fines, 9 Tuning, 31+ user-defined — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv). Fuelio cost categories are global (car-independent), so non-standard ids may be reassigned on import — [FuelioImport README](https://github.com/Programistyk/FuelioImport); the converter uses ids >= 50 ("SAFE_CATEGORY_ID = 50") for custom categories — [fueliobackupbuilder.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/fueliobackupbuilder.class.php).
- `## Category` (`IdCategory`,`Name`: 1 Private, 2 Work) appears to be trip/usage categories — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv).

### Inferences
- Map: date=`Data`; odometer=`Odo*` (float; can be "212477.0"); volume=`Fuel*`; total_cost=`Price*`; unit_price=`VolumePrice`; is_full=`Full`=="1"; missed_previous=`Missed`=="1"; station name=`City*`; lat/lon; tank=`TankNumber`; fuel_grade=`FuelType`. If total is 0 but VolumePrice present (or vice versa), derive one from the other.
- Fuelio's own computed consumption column should be ignored and recomputed (often 0.0).
- For a UAE app: map 110/111 -> Special 95, 112/113 -> Super 98, 114 -> E-Plus 91 not exact (Fuelio's octane list includes "91" = 106), 201 -> Diesel; low confidence, verify against user data.

### Gaps
- No city/highway percentage field found in any sample or parser; Fuelio does not appear to export one.
- Meaning of `TankCalc`, `flag`, `idR`, `read`, `priority`, `color` not documented anywhere found.

## How users export from Android and iOS (files, locations, formats)

### Takeaway
Android Fuelio writes per-vehicle backup files named `vehicle-<id>-sync.csv` (zipped as `vehicle-<id>-sync.csv.zip`) to `backup-sync`, and manual CSV exports to `backup-csv`, locally (`/Fuelio`) and/or on Google Drive (`Android/Fuelio`) or Dropbox (`Apps/Fuelio`). iOS app advertises CSV read/export and iCloud sync; no iOS file-format specifics found.

### Cited Findings
- Fuelio FAQ (via search snippet): Google Drive files saved in folder `Android/Fuelio`, containing `backup-csv` and `backup-sync` directories; `/backup-csv/` holds manual selective backups made via Settings > Backup > Export CSV — [Fuelio FAQ: Backup files](https://fuel.io/faq_backup_help.html) (not directly fetched; snippet only).
- Fuelio FAQ (snippet): Dropbox stores Android app files under `Apps`, so look for `Apps/Fuelio`; a CSV placed in `/Apps/Fuelio/backup-csv/` can be imported from the Dropbox screen; local exports go to a `/Fuelio` folder — [Fuelio FAQ](https://fuel.io/faq_backup_help.html).
- Google Drive backups are per-vehicle ZIP files each containing one CSV; filename pattern `vehicle-{id}-sync.csv.zip` containing `vehicle-{id}-sync.csv`; the vehicle id comes from the filename (not the CSV contents); multiple backups may exist and the importer picks the latest by modifiedTime; CSV decoded as UTF-8 — [tigattack fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py).
- HA-Fuelio reads a local `fuelio/vehicle-1-sync.csv.zip` (synced from Drive via rclone), takes the first `.csv` member, decodes `utf-8-sig` (BOM-tolerant) — [HA-Fuelio parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py), [README](https://github.com/Jocke1970/HA-Fuelio).
- fuelio2fuelly usage example: `python convert.py vehicle-3-sync.csv` (unzipped sync file) — [dundee/fuelio2fuelly](https://github.com/dundee/fuelio2fuelly).
- iOS: App Store listing says Fuelio "is able to read and export data to CSV format" (iOS 13+) — [App Store](https://apps.apple.com/app/fuelio-gas-tracker-mpg/id1487753318); third-party listing says iOS uses iCloud to keep data up to date — [apptail.io](https://apptail.io/app/fuelio-gas-log-mpg-tracker-YAt/czechia).
- Fuelio file format holds only one car per file — [FuelioImport README](https://github.com/Programistyk/FuelioImport).

### Inferences
- Importer should accept: a `.csv`, a `.csv.zip` (unzip, pick `*.csv`), and multiple files at once (one per vehicle). Strip a UTF-8 BOM.
- Users with N vehicles have N files; ask user or use Vehicle `Name`/`Plate` to label.

### Gaps
- No evidence found that Fuelio exposes a raw `.db`/SQLite file to users; a repo "maskimko/FuelIOExporter" ("Database exporter form FuelIO") exists but its contents could not be inspected — [GitHub](https://github.com/maskimko/FuelIOExporter).
- iOS export file layout (same sections? date format?) unverified. Whether the iOS CSV is identical to the Android sync CSV is unknown.
- app-tatsujin.com (Japanese blog) pages describe conflicting Dropbox paths and a five-column CSV; unsourced and inconsistent, not relied on.

## Open-source parsers and how other apps implement Fuelio import

### Takeaway
No mainstream app (LubeLogger, Hammond, Fuelly) has a native Fuelio importer; the community bridges are small scripts. The best references are tigattack/lubelogger-fuelio-importer (Python, section splitter + typed models), Jocke1970/HA-Fuelio (strict Python section parser), dundee's fuelio2fuelly/fuelio2spritmonitor (positional), and Programistyk/FuelioImport (PHP writer into Fuelio format, documents field semantics). Drivvo Pro claims to restore from Fuelio.

### Cited Findings
- **tigattack/lubelogger-fuelio-importer** (Python 3.12+, LubeLogger >=1.5.7): pulls latest `vehicle-N-sync.csv.zip` from a Google Drive folder via service account, extracts CSV, finds section by `line.lstrip().strip('"').startswith("## Log")`, ends at next `##`, feeds lines after marker to `csv.DictReader`; requires columns `Data`, `Odo (mi)`, `Fuel (litres)`, `Full`; parses date strictly as `%Y-%m-%d %H:%M`; skips invalid rows; dedupes by date+mileage, `--clobber` overwrites — [repo](https://github.com/tigattack/lubelogger-fuelio-importer), [fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py), [flio_models.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/flio_models.py). Announced in [LubeLogger discussion #411](https://github.com/hargata/lubelog/discussions/411). Weakness: hard-codes `Odo (mi)`, so km users' files fail the required-column check (inference from code).
- **Jocke1970/HA-Fuelio** (Home Assistant integration): `_section_rows` uses `csv.reader(strict=True)`, skips blank rows, a 1-cell row starting `## ` starts a section, rejects duplicate sections, duplicate/empty headers, and rows whose length differs from header; dates tried as `%Y-%m-%d %H:%M:%S`, `%Y-%m-%d %H:%M`, `%Y-%m-%d`; numbers parsed as `Decimal` (dot decimal); clamps Fuelio's recurring-cost bug dates like `2026-09-31` to month end; only supports one vehicle and metric (`DistUnit`=="0" and `FuelUnit`=="0") — [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).
- **dundee/fuelio2fuelly**: positional parse of `## Log`; outputs Fuelly CSV `fuelup_date, odometer, litres, partial_fuelup, price` from columns [0],[1],[2], `0 if Full=='1' else 1`, [13] (VolumePrice) — [convert.py](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/convert.py).
- **dundee/fuelio2spritmonitor**: same positional parse, sorts by odometer, uses `int(odo)` and `strptime('%Y-%m-%d')`, outputs `;`-separated Spritmonitor CSV with `dd.mm.yyyy` dates and type 1 full / 2 partial / 3 first — [convert.py](https://raw.githubusercontent.com/dundee/fuelio2spritmonitor/master/convert.py). Would crash on newer files with `"212477.0"` odometer or `HH:MM` times (inference).
- **Programistyk/FuelioImport** (PHP, MIT; hosted at fuelio.programistyk.pl): converts Motostat CSV, aCar .abp, Fuel Log CSV, Drivvo CSV INTO Fuelio format; notes Drivvo backups are locale-dependent (ES/EN/PL work; others may misread the "full" flag) — [README](https://github.com/Programistyk/FuelioImport). Its writer uses `DATE_FORMAT = 'd.m.Y'` with vehicle `ImportCSVDateFormat = 'dd.MM.yyyy'` — [fueliobackupbuilder.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/fueliobackupbuilder.class.php), [vehicle.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/vehicle.class.php).
- Other GitHub repos found via search (not inspected): oroszgy/fuelio2toshl ("import fuel.io backups to Toshl"), albertopasqualetto/fuelio2wallet (Fuelio -> BudgetBakers Wallet via Dropbox), baptistebisson/fuelio-to-mysql, TwoWheelDev/FuelioWeb, geoai777/aCarConverter (aCar -> Fuelio CSV), marten-cz/FuelioImport (fork), genex89/ha-fuelio-dropbox — [GitHub search API results](https://api.github.com/search/repositories?q=fuelio).
- **LubeLogger** native CSV import maps headers case-insensitively: date `date|fuelup_date`; odometer `odometer|odo`; fuel `gallons|liters|litres|consumption|quantity|fuelconsumed|qty`; cost `cost|total cost|totalcost|total price`; `price`; partial `partial_fuelup|partial tank|partial_fill`; `isfilltofull|filled up`; missed `missed_fuelup|missedfuelup|missed fill up|missed_fill`; `notes|note` — [ImportMappers.cs](https://raw.githubusercontent.com/hargata/lubelog/main/MapProfile/ImportMappers.cs). It does not natively understand the sectioned Fuelio file (no Fuelio mention in mappers); Fuelly format is its documented import path — [search summary of LubeLogger docs](https://docs.lubelogger.com/Records/Fuel%20Records).
- **Hammond** (AlfHou/hammond): import options are Clarkson migration and Fuelly; no Fuelio importer found — [makz27/hammond fork](https://github.com/makz27/hammond).
- **Fuelly**: no Fuelio importer; fuelio2fuelly is the bridge — [fuelio2fuelly](https://github.com/dundee/fuelio2fuelly).
- **Drivvo**: store listing says Pro can restore data from other apps including Fuelio; a user review says other-app import is allowed only once — per search summary of [Drivvo App Store listing](https://apps.apple.com/app/id1206041425) (low confidence; not directly verified).
- **Simply Auto**: no evidence found of a Fuelio importer.

### Inferences
- A new app can implement a better importer than any of these: section-aware, header-name-based, unit-aware (read Vehicle `DistUnit`/`FuelUnit`), tolerant date parsing, importing Costs + categories + stations too.

### Gaps
- Drivvo/Simply Auto implementation details unavailable (closed source).

## Edge cases: multiple vehicles, dual tanks, locale decimals, date formats, encoding

### Takeaway
Expect: one vehicle per file; dates mostly `yyyy-MM-dd` or `yyyy-MM-dd HH:mm` (field `ImportCSVDateFormat` declares the format, and older/import files may use `dd.MM.yyyy`); dot decimals; comma separator with all fields double-quoted; UTF-8 with non-ASCII station names; ordering not guaranteed.

### Cited Findings
- Separator/quoting: comma-separated, every field double-quoted (with occasional unquoted empty field) — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv).
- Decimal separator: `.` even for a Czech user (Czech locale normally uses `,`) — [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv).
- Dates: Log `Data` = `2018-10-07` (date only, 2018) vs `2024-03-10 16:01` (2024); Costs `Date` = `2024-02-08 14:57`, `RemindDate` = `2024-07-19`; Vehicle `ImportCSVDateFormat` = `yyyy-MM-dd` in both samples — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv). HA-Fuelio also accepts `%Y-%m-%d %H:%M:%S` — [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py). The import-side converter writes `dd.MM.yyyy` — [vehicle.class.php](https://raw.githubusercontent.com/Programistyk/FuelioImport/master/lib/fuelioimporter/vehicle.class.php).
- Invalid dates: Fuelio can emit nonexistent dates like `2026-09-31` for recurring expenses — [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).
- Numbers: odometer may be integer (`424`) or float (`212477.0`); flags may be `1` or `1.0`; `ExcludeDistance` `0` or `0.0` — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv), [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv), [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).
- Encoding: UTF-8 (station "Hlavní město Praha, Československého exilu - OMV"); decoders use `utf-8` / `utf-8-sig` — [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv), [fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py), [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).
- Order: Log rows newest-first in sample #2; fuelio2spritmonitor re-sorts by odometer — [sample #2](https://raw.githubusercontent.com/dundee/fuelio2fuelly/master/example-fuelio.csv), [convert.py](https://raw.githubusercontent.com/dundee/fuelio2spritmonitor/master/convert.py).
- Multiple vehicles: separate files per vehicle; vehicle id only in filename — [fuelio.py](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/src/fuelio.py), [FuelioImport README](https://github.com/Programistyk/FuelioImport).
- Dual tank: Vehicle has `TankCount`, `Tank1Type`, `Tank2Type`, `Tank1Capacity`, `Tank2Capacity`, `FuelUnitTank2`, `FuelConsumptionTank2`; Log rows carry `TankNumber` (1 or 2) — [sample #1](https://raw.githubusercontent.com/tigattack/lubelogger-fuelio-importer/main/fuelio_backup_sample.csv). HA-Fuelio defaults missing TankNumber to "1" — [parser.py](https://raw.githubusercontent.com/Jocke1970/HA-Fuelio/main/custom_components/fuelio/parser.py).

### Inferences
- Date parser order: `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd HH:mm`, `yyyy-MM-dd`, then the pattern in `ImportCSVDateFormat` (convert Java-style `dd.MM.yyyy` to the platform's format), then `dd.MM.yyyy`. Times have no timezone: treat as device-local.
- Numbers: accept `.` and, as fallback, a single `,` decimal (hand-edited files opened in Excel in comma locales may be re-saved with `;` separators and `,` decimals — sniff the delimiter).
- Dual-tank: split consumption per `TankNumber`; tank 2 may use a different fuel unit (`FuelUnitTank2`, e.g. LPG/CNG), so convert volume per tank.
- Units for UAE (km, litres, AED): convert miles/gallons using the Vehicle codes; currency is NOT stored in the file — ask the user.

### Gaps
- No confirmed sample with a comma decimal, `;` separator, gallons, or TankCount=2.
- Whether Fuelio's export ever localizes dates or decimals is unconfirmed (official FAQ not reachable).
