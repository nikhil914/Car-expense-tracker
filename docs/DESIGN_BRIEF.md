# Sayyara: design brief

Use this brief to ask for UI/UX design suggestions. It describes the app as it exists today (v0.1.3), who it is for, every screen and the data on it, the technical constraints, and the apps to draw inspiration from.

---

## 1. Product in one paragraph

Sayyara ("car" in Arabic) is an Android-first app for UAE drivers that tracks fuel economy and every car cost: fuel, Salik and Darb tolls, parking, Mulkiya (registration) renewal, insurance, service and fines. It is a better alternative to Fuelio, a popular fuel-log app. Its edge comes from the UAE itself. Fuel prices are set nationally every month (Super 98, Special 95, E-Plus 91, Diesel), so the app knows the exact price of every litre without crowdsourcing. It also handles UAE-specific costs and reminders, works in Arabic and English, and can detect refuels automatically from the car's head unit (Android Auto / Android Automotive).

## 2. Users

- **Primary:** UAE residents who own or lease a car. They are mostly expatriate professionals and Emirati families, aged 22–50, with one or two cars per household. They pay for fuel at ADNOC, ENOC or Emarat stations, often in round amounts such as AED 50 or AED 100.
- **Secondary:** people switching from Fuelio who want to bring their history with them, and EV or plug-in hybrid drivers.
- **Context of use:** standing at the pump or just after refuelling, with one hand and limited time, often in strong sunlight. Quick glances at the home screen and a monthly review of costs.
- **Languages:** English and Arabic. Arabic flips the whole layout to right-to-left (RTL).

## 3. Design goals

1. **Log a fill-up in three taps or fewer.** The odometer, fuel grade and official monthly price are pre-filled. Full tank is the default.
2. **Show the answer at a glance.** The home screen should answer "how efficient is my car?", "how much did I spend this month?" and "what is due soon?" without scrolling.
3. **Explain the numbers.** Each consumption figure should say what it is based on, for example "Based on 3 fills since 2 Nov". Partial fills that are still waiting for the next full tank should be visible.
4. **Make it feel local.** Use AED, km and litres. Use Salik, Darb, Mulkiya and the emirates, and treat Arabic as a first-class language with properly mirrored layouts.
5. **Stay calm and trustworthy.** No ads, no dark patterns, and never a paywall on export.
6. **Read well in sunlight.** High contrast, large figures and large touch targets.

## 4. Visual system today (open to redesign)

- **Framework:** Jetpack Compose with Material 3 (Material You). Dynamic colour is on by default, with a brand fallback palette.
- **Brand fallback palette:** desert sand plus deep teal, in light and dark themes.
- **Hero number:** a 44 sp bold figure on the home screen.
- **Charts:** drawn by hand on a Compose Canvas: line, stacked bar and legend. They can be restyled freely.
- **Icons:** Material Symbols, auto-mirrored in RTL.
- **Navigation:** a bottom navigation bar with four tabs (Home, History, Stats, More), plus an extended "Add fill-up" floating action button on Home.
- **Fill-up form:** a modal bottom sheet over any screen.

**Known problems the user has raised:**
- The overall look and feel "is not good". It is generic Material 3 with little personality.
- The first card on the home screen wrapped its values: "km/L" fell onto a second line. That is now fixed by showing the numbers only and the unit once underneath, but the card still needs a proper design.

## 5. Screen inventory

### 5.1 Onboarding (first launch)
- **Step 1:** choose a language (English or العربية), then read and accept the privacy consent required by the UAE data protection law (PDPL). Continue is disabled until consent is given.
- **Step 2:** add the first vehicle (form in 5.9), or import from Fuelio instead.

### 5.2 Home (dashboard)
- **Top bar:** the vehicle name, with a dropdown when the user has more than one car.
- **Card 1, economy hero:** two figures side by side, "Last" and "Average", each a number only. The unit (km/L, L/100 km or MPG) appears once below them.
  - An explanation line, for example "Based on 3 fills since 2 Nov", or "Log two full-tank fills to see your consumption".
  - Optionally, "2 partial fills waiting for the next full tank".
  - A divider, then "Total cost: AED 0.42 per km".
  - Tapping the card opens Stats.
- **Card 2, this month's spend:** the total in AED (large), then a row per category with a coloured dot, the category name and the amount. Categories are Fuel, Salik, Darb, Parking, Mulkiya, Inspection, Insurance, Service, Tyres, Fine, Wash and Other.
- **Card 3, fuel price:** "Special 95 price: AED 4.04 / L".
  - A "Provisional" or "Forecast" badge when next month's price has not been announced yet.
  - "Price for November 2026".
  - A trend arrow with text such as "24 fils cheaper than October".
- **Card 4, upcoming:** reminders with an icon, a title and a due line such as "In 12 days or 800 km" or "Overdue by 3 days" (shown in red).
  - If no Mulkiya reminder exists, a call-to-action to "Set up Mulkiya renewal".
  - A "All reminders" link.
- **Floating action button:** "+ Add fill-up".
- **Empty state:** "No vehicle yet", with an Add vehicle button.

### 5.3 Add / edit fill-up (bottom sheet)
- **Odometer:** pre-filled and estimated from the user's driving, with the hint "Last: 45,210 km".
- **Amount mode toggle:** "By litres" or "By AED". The other value is computed and shown, for example "≈ 32.5 L" or "Total ≈ AED 131".
- **Price per litre:** pre-filled with the official UAE price for that month, labelled "Official UAE price for Nov".
- **Fuel grade chips:** Super 98, Special 95, E-Plus 91, Diesel.
- **Full tank switch:** on by default. When the total is a round AED amount, a hint chip suggests "Looks like a partial fill. Mark as partial?".
- **Optional fields:**
  - tank-level sliders for before and after the fill (0–100%)
  - "I missed logging a fill-up"
  - station brand chips (ADNOC / ENOC / Emarat)
  - "Scan receipt", which uses OCR on a photo to fill litres, price and total
- **Validation:** warnings appear inline, for example "Did you miss a fill-up?".
- **Fill-ups detected by the car:** they open this sheet pre-filled and labelled "Detected by your car".
- **Buttons:** Save (primary), and Delete when editing.

### 5.4 History
- A single timeline of fill-ups and expenses, newest first.
- **Fill-up row:** "32.5 L · Special 95", with the odometer, the km since the previous fill, and Partial or Missed badges.
- **Expense row:** the category, the title, the amount, and an "Income" marker for income entries.
- Swipe to delete, with an Undo snackbar. Tap a row to edit it. A button adds an expense.

### 5.5 Add / edit expense
- **Fields:** category chips, amount in AED, date, odometer (optional), title, note, and an Income toggle.
- **Parking helper**, shown when the category is Parking:
  - emirate, zone (for example 365C) and hours
  - the hourly rate
  - a "Pay by SMS" button that opens the messaging app with the text pre-filled (Dubai 7275, Abu Dhabi 3009, Sharjah 5566)

### 5.6 Stats
- **Period chips:** 3 months, 6 months, 12 months, All.
- **Summary card:** total spend, distance and cost per km.
- **Charts:**
  - consumption trend (line)
  - monthly spend by category (stacked bars)
  - cost per km by month (line)
  - official fuel price history, with the user's own fills as dots

### 5.7 Trip cost (UAE only)
- **Input mode:** either coordinates (from and to latitude/longitude, with a swap button) or a distance in km typed in by hand.
- **Fields:**
  - departure date and hour
  - fuel grade
  - consumption, pre-filled from the user's average
  - round trip and passengers
  - parking: emirate, zone and hours
- **Result card:**
  - a large total in AED, with a Provisional or Forecast badge
  - the range from low to high
  - lines for fuel (litres and AED), tolls (each Salik or Darb gate by name), parking, and cost per person
  - a note that UAE fuel prices change on the 1st of every month
- Today the screen is plain text fields. **A map-based origin/destination picker is a desired improvement.**

### 5.8 More (menu)
List rows for Vehicles, Reminders, Trip cost, Import from Fuelio, Settings and About.

### 5.9 Vehicles
- **List:** each row shows the name, "make · model · year · plate" and a Selected or Select action.
- **Add/edit form:** name, make, model, year, plate, emirate chips, fuel grade chips (including Electric), and tank capacity in litres.
  - A "Plug-in hybrid" switch, which reveals the battery capacity in kWh.
  - A Delete button when editing.

### 5.10 Reminders
- **List:** each reminder shows its icon, title and due line, plus a "Done" action. Repeating reminders roll forward.
- Above the list, a "Set up Mulkiya renewal" button.
- **Editor:**
  - type chips: Mulkiya, Inspection, Insurance, Service, Oil change, Tyres, Custom
  - title
  - due date and/or due odometer
  - repeat every N months and/or N km
  - how many days before to notify
- **Mulkiya wizard:** pick the registration expiry date, preview the generated chain (inspection 30 days before, insurance 21 days before, then renewal), and create it.

### 5.11 Import from Fuelio
- A short instruction on how to export from Fuelio, then a "Choose file" button (.csv or .zip).
- **Preview card:** "1 vehicle, 214 fill-ups, 37 expenses", plus warnings.
- An Import button, then "Import complete" with an Undo import button.

### 5.12 Settings
- **Language:** System, English or العربية.
- **Economy unit:** km/L (the UAE default), L/100 km or MPG.
- **Theme:** System, Light or Dark.
- **Dynamic colour:** on or off.
- **Export:** "Export all data (CSV)".

### 5.13 About and privacy
App description, privacy text (data stays on the device unless backup is turned on), and a disclaimer about the source of fuel prices.

### 5.14 Outside the main app
- **Home-screen widget:** the latest km/L, this month's spend, the Special 95 price, and an "Add fill" button.
- **Quick Settings tile:** one tap opens Add fill-up.
- **App shortcuts:** Add fill, Add expense, Trip cost.
- **Notifications:** reminder due or overdue; "UAE fuel prices for December: Super 98 AED x"; refuel detected by the car.
- **Android Auto and Android Automotive (car screens):** these use fixed Google car templates, so visual freedom is limited.
  - a list of nearby fuel stations
  - this month's UAE prices
  - a "Refuel detected: ~32 L (AED 129 at Special 95, Nov price)" screen with Save, Edit on phone and Dismiss

## 6. Data the design must accommodate

- **Numbers:** they can be long, for example "AED 12,345.67" or "123,456 km". Arabic may use Arabic-Indic digits (٠١٢٣). Never let a figure wrap onto two lines; shrink it or abbreviate it instead.
- **Arabic text:** Arabic labels are often 20–40% longer or shorter than English. Every layout must mirror correctly in RTL.
- **Font scale:** text must still work at a system font scale of 1.3× or more.
- **Empty and partial states:** no vehicle, no fills, only one fill (so no consumption figure yet), price not yet announced, and a car that does not report its fuel level.
- **Several vehicles**, which needs a vehicle switcher.
- **EVs and plug-in hybrids:** kWh, kWh/100 km and battery %.

## 7. Technical constraints for the design

- **Toolkit:** Android, Jetpack Compose and Material 3 components, which can be themed heavily. No web views.
- **Sizes and modes:** phones first, from 360 dp wide. Must work in light, dark and RTL.
- **Touch targets:** at least 48 dp. Contrast must meet WCAG AA.
- **Car screens:** Google's templates only, so no custom drawing.
- **Charts:** any style can be drawn on a Canvas, but suggestions should stay within what is practical to draw.

## 8. Reference apps for inspiration

| App | What to take from it |
|---|---|
| **Fuelio** (Android/iOS) | The direct competitor. Shows what users expect: fill-up log, costs, charts. Its UI is dense and dated, and that is the thing to beat. |
| **Drivvo** | Vehicle cost categories and reports. Avoid its ads and cluttered screens. |
| **Copilot Money** (iOS) | Premium personal-finance dashboard: calm cards, spend by category, clear trend charts, tasteful colour. The closest match to the home screen we want. |
| **YNAB** | Clear handling of money states (over or under), friendly copy, monthly framing. |
| **Revolut / Wise** | Large figures with small units, transaction timelines, category icons, polished bottom sheets. Good model for the History screen. |
| **Monzo** | Warm colour, round category icons, friendly empty states, a feed-style timeline. |
| **Strava** | Stats screens with periods, clear charts, a single headline metric with supporting numbers. |
| **Google Fit / Samsung Health** | Glanceable rings and progress figures, Material You done well. |
| **Tesla app / Polestar app** | Car-centric hero: vehicle image or illustration, range and energy shown simply, a premium dark theme. |
| **Waze / Google Maps** | The Trip cost screen: map-first input with origin and destination chips and a route summary sheet. |
| **Careem / Talabat** | UAE-native apps done well in Arabic and English. Shows how RTL, Arabic typography and AED pricing look when handled properly. |
| **ADNOC Distribution app / ENOC app** | Local fuel-station brand language that UAE drivers already know (fuel grade colours, station cards). |
| **RTA Dubai app** | How Salik, parking and Mulkiya are presented officially, so terminology and icons feel familiar. |
| **Material 3 Expressive (Google, 2025)** | Current Android design direction: bolder type, shape morphing, emphasised cards. |

## 9. What to ask the designer for

1. A visual direction: palette (light and dark), typography (Latin plus Arabic, for example Inter or Roboto Flex with IBM Plex Sans Arabic or Noto Kufi Arabic), shape and elevation style, iconography, and how the brand should feel.
2. A redesign of the **home dashboard**, especially the economy hero card, so that Last and Average read well with the unit shown once.
3. The **add fill-up sheet** with three taps or fewer.
4. The **History** timeline and the **Stats** charts.
5. The **Trip cost** screen with a map-based picker.
6. Empty, loading and error states, and the onboarding flow.
7. Arabic RTL versions of the home screen and the fill-up sheet.
8. Widget and notification styling.
