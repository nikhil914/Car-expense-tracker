# UAE Car Ownership Cost Ecosystem and Local Apps (for a vehicle expense tracker)

Method note: Research done 2026-11-05 through WebSearch result summaries only. WebFetch failed (hosts not resolvable in this session: gulfnews.com, yallamotor.com), so primary pages were not read in full. Many sources are aggregators/insurance-broker blogs; the most authoritative cited items are WAM, Dubai Media Office, DFM filings, admobility.gov.ae, dubaipulse.gov.ae and app store listings. Treat all fee figures as "verify before hard-coding"; build them as remote-configurable tariff tables.

## Salik (Dubai tolls): gates, pricing, accounts, integration, company status

### Takeaway
Salik runs 10 gates. Since 31 Jan 2025 it uses variable pricing: AED 6 at peak, AED 4 off-peak, and free from 1-6am. A 5% VAT layer was reportedly added in mid-2026, giving AED 6.30 and AED 4.20, but sources disagree on the start date. No public consumer API was found. Integration must therefore be manual, by import, or by parsing SMS/email notifications.

### Cited Findings
- 10 gates since 24 Nov 2024, up from 8. The two new gates are Business Bay (Business Bay Crossing on Al Khail Road) and Al Safa South (Sheikh Zayed Road, between Al Meydan and Umm Al Sheif streets) — [Khaleej Times](https://khaleejtimes.com/uae/transport/dubai-salik-announces-2-new-toll-gates-to-be-operational-from-november-24?amp=1); [Gulf Business](https://gulfbusiness.com/2024/transport/salik-new-toll-gates-operational-from-nov-24/)
- The other eight gates: Al Barsha, Al Garhoud Bridge, Al Maktoum Bridge, Al Mamzar South, Al Mamzar North, Al Safa (North), Airport Tunnel, Jebel Ali — [Khaleej Times](https://khaleejtimes.com/uae/transport/dubai-salik-announces-2-new-toll-gates-to-be-operational-from-november-24?amp=1)
- Linked-gate rule: passing Al Safa North and Al Safa South in the same direction within one hour is charged once. Al Mamzar North/South have historically worked the same way, but this was not re-verified. — [Khaleej Times](https://khaleejtimes.com/uae/transport/dubai-salik-announces-2-new-toll-gates-to-be-operational-from-november-24?amp=1)
- Gate valuations: Business Bay AED 2,265m and Al Safa South AED 469m — [WAM](https://www.wam.ae/en/article/1438x68-salik-announces-financial-valuation-two-new-gates)
- Variable pricing took effect 31 Jan 2025 — [Platinumlist](https://platinumlist.net/guide/new-salik-toll-gate-pricing-in-dubai-free-hours-and-peak-charges-explained/); [Citti Magazine](https://www.cittimagazine.co.uk/news/road-user-charging-tolling/salik-introduces-variable-toll-pricing-to-optimise-traffic-flow-in-dubai.html). One guide dates it 31 Jan 2026, which looks like an error — [Platinumlist](https://platinumlist.net/guide/dubais-salik-variable-toll-rates-launching-january-31/)
- Weekdays (Mon-Sat): AED 6 peak (6-10am and 4-8pm); AED 4 off-peak (10am-4pm and 8pm-1am); free 1-6am — [Selfdrive.ae](https://selfdrive.ae/blog/dubai-salik-charges-explained-how-much-will-you-pay-in-2026); [Pitstop Arabia](https://www.pitstoparabia.com/en/news/salik-peak-hours-timings-toll-rates)
- Sundays: flat AED 4 during chargeable hours, free 1-6am. Public holidays and special events follow the Sunday-style rate per guides (not re-verified). — [Selfdrive.ae](https://selfdrive.ae/blog/dubai-salik-charges-explained-how-much-will-you-pay-in-2026)
- Ramadan: temporary special timetables, including Ramadan 2026. Standard pricing resumes after Ramadan. — [ArabWheels](https://www.arabwheels.ae/blog/salik-toll-changes-ramadan-2026-dubai/)
- VAT 2026: one guide says 5% VAT has applied since 1 June 2026, making AED 6.30 peak and AED 4.20 off-peak. Another says 1 July 2026. CONFLICT, unverified against Salik/FTA primary sources. — [Selfdrive.ae](https://selfdrive.ae/blog/dubai-salik-charges-explained-how-much-will-you-pay-in-2026); [PropertyFinder](https://www.propertyfinder.ae/blog/dubai-salik-tolls-commute-costs/)
- Salik Company PJSC is listed on DFM (ticker SALIK). Q1 2026: revenue AED 728.9m (down 3.0% YoY), 197.2m total trips — [WAM](https://www.wam.ae/en/article/c05qhcg-salik-reports-revenue-aed-7289-million); [Emirates 24/7](https://www.emirates247.com/business/salik-reports-q1-revenue-of-aed-7289-million/1496)
- H1 2026: revenue AED 1,412.0m (down 7.5%), net profit AED 704.0m (49.9% margin), 383.8m total trips (down 9.5%), 278.5m chargeable trips (down 12.5%). Salik blamed a slowdown "following the exceptional regional events that began in late February 2026". — [Dubai Media Office](https://mediaoffice.ae/en/news/2026/august/04-08/salik-reports-aed-141-billion-revenue-aed-704-million-profit); [DFM press release PDF](https://feeds.dfm.ae/documents/2026/Aug/5/9069488c-a65b-4802-aaca-09c4748d7296/Salik%20Q2%202026%20Press%20Release%20EN.Pdf.pdf); [Khaleej Times](https://www.khaleejtimes.com/business/saliks-h1-profits-drop-87-as-fines-number-of-trips-decline)
- Salik is expanding beyond tolls. Parkonic runs more than 150 parking sites, and 18 of them deduct parking fees from the Salik account automatically if not prepaid. Salik also handles parking at some Abu Dhabi malls with Parkonic. — [Gulf News](https://gulfnews.com/living-in-uae/transport/where-you-can-use-salik-to-pay-for-parking-in-the-uae-1.500198394); [Khaleej Times](https://www.khaleejtimes.com/transport/dubai-salik-parkonic-paid-parking-abu-dhabi-malls)
- Dubai Pulse has a Salik tariff dataset (CSV). It is marked obsolete/not updated and needs a permission request. It holds no per-user trip data. — [Dubai Pulse](https://www.dubaipulse.gov.ae/data/rta-archive/rta_salik_tariff-open)
- No public Salik developer API for consumer trip or balance data was found — search returned nothing relevant (no source).

### Inferences
- Model Salik as a time-of-day and day-of-week tariff table with linked-gate dedup, a free window, a VAT flag and Ramadan/holiday override calendars. Push it from a remote config, because rules changed in 2024, 2025 and 2026.
- With a gate-geofence list (lat/lon), the app could estimate toll cost per trip from GPS. That needs consent for location data under the PDPL (see last section).
- Reconciliation is best done via (a) Android SMS parsing of Salik trip/low-balance SMS (needs READ_SMS or the SMS Retriever / user-share flow; Google Play restricts READ_SMS), (b) email-forward parsing if Salik sends statements, (c) CSV/PDF statement import from the Salik web portal, or (d) manual "top-up" entries. iOS cannot read SMS. Use share-sheet text or a forwarding email address instead.
- Track a Salik top-up separately from a toll usage event. A top-up is a prepaid transfer and should not be counted as a cost.
- Salik also deducts parking fees (Parkonic sites). The parser should classify Salik debits as toll or parking.

### Gaps
- Exact Salik SMS/email message formats, sender IDs and whether eSalik e-statements exist were not found. Validate with real user samples.
- Low-balance rules (minimum balance, grace period, AED 50 per-trip violation after grace) were not confirmed in this research.
- Salik app feature list and deep-link/URL schemes were not found.
- Gate GPS coordinates were not sourced. Derive them from OSM or build them by hand.
- Primary-source confirmation of the 2026 VAT start date is missing.

## Darb (Abu Dhabi tolls): gates, peak pricing, caps, exemptions, TAMM

### Takeaway
Darb charges AED 4 per crossing at peak times only: 7-9am and 3-7pm on working days. Sundays and public holidays are reported free. The daily cap (AED 16) and monthly caps (AED 200/150/100) were removed for private vehicles from 1 Sep 2025, per the official ITC/AD Mobility page. Registration is via the Darb app, the website or TAMM.

### Cited Findings
- Daily and monthly caps "have been removed" for vehicles passing gates in peak hours — [AD Mobility (official)](https://admobility.gov.ae/en/darb-daily-and-monthly-caps); the older ITC page still shows AED 16 daily and AED 200/150/100 monthly — [ITC](https://itc.gov.ae/en/darb-daily-and-monthly-caps)
- From 1 Sep 2025: caps lifted and charging hours extended. Peak is 7-9am and 3-7pm — [Gulf News](https://gulfnews.com/uae/transport/abu-dhabi-revises-darb-toll-rules-lifts-daily-and-monthly-caps-1.500233627); [The National](https://www.thenationalnews.com/news/uae/2025/08/15/abu-dhabi-darb-toll-timings-extended); [AD Mobility news](https://admobility.gov.ae/en/news/amendments-to-road-toll-executive-regulations-in-abu-dhabi)
- Fee is AED 4 per crossing at peak. Sources disagree on chargeable days (Mon-Sat vs Mon-Fri); Sundays and public holidays are reported toll-free — [Shory](https://www.shory.com/car-insurance/blog/darb-timing/); [Gulf News explainer](https://gulfnews.com/living-in-uae/transport/darb-toll-system-abu-dhabi-explained-fees-timings-and-exemptions-1.500219396)
- Original gates: Sheikh Zayed Bridge, Sheikh Khalifa Bridge, Al Maqta'a Bridge, Mussafah Bridge — [Gulf News](https://gulfnews.com/living-in-uae/transport/darb-toll-system-all-you-need-to-know-about-abu-dhabis-road-toll-1.1607436648969)
- An unverified 2026 guide claims the network was reshaped: 6 gates, 4 renamed, 2 charging 24/7 — [Shory new gates guide](https://www.shory.com/car-insurance/blog/new-darb-toll-gates-in-abu-dhabi-locations-timings-charges-guide/); [OneClickDrive](https://blog.oneclickdrive.com/abu-dhabi-toll-gate-timing-new-darb-gates/)
- Registration is mandatory, including for exempt users, and is done via the portal, the Darb app or TAMM. Unregistered vehicles get a 10-working-day grace period from the first crossing — [Gulf News](https://gulfnews.com/living-in-uae/transport/headed-to-abu-dhabi-register-for-the-darb-toll-system-in-8-easy-steps-1.1632647833941); [Bayut](https://www.bayut.com/mybayut/steps-to-register-abu-dhabi-salik)
- People of Determination exemption: one vehicle, owned by a first- or second-degree relative. Request is made in the Darb account with the PoD card, and the exemption can be transferred between vehicles. Other exempt categories are listed in KT coverage — [Khaleej Times](https://www.khaleejtimes.com/uae/abu-dhabi-tolls-who-are-exempted-from-fines); [PropertyFinder](https://www.propertyfinder.ae/blog/darb-toll-gate)
- Historical (2021) exemptions included low-income nationals, retirees and PoD — [WAM](https://wam.ae/en/details/1395302893293)

### Inferences
- With caps removed, Darb cost now scales linearly with peak crossings: daily commuters do 2 crossings x AED 4 x about 22 days, roughly AED 176/month per vehicle. A "peak avoidance savings" insight (shift by 1 hour) is valuable.
- The tariff engine for Salik can be reused, with a separate Darb profile (peak-only charging, AED 4, no caps, holiday calendar).

### Gaps
- Current 2026 gate list/names and any 24/7 gates are unconfirmed from an official source.
- No public Darb API or open-data trip feed was found. TAMM is a government super-app with no known third-party API for personal data.

## Parking: Dubai (Parkin/RTA), Abu Dhabi (Mawaqif), Sharjah, Parkonic, SMS payments

### Takeaway
Dubai public parking is operated by Parkin PJSC (DFM-listed). Variable tariffs started 4 Apr 2025: AED 6/hour for premium spaces at peak (8-10am and 4-8pm), AED 4 for standard spaces, and AED 25/hour event pricing near venues. SMS payments are widespread: 7275 in Dubai, 3009 in Abu Dhabi, 5566 in Sharjah. Each SMS produces a confirmation SMS, which makes SMS a good capture channel for automatic expense logging.

### Cited Findings
- Parkin variable tariff: peak covers 6 of 14 chargeable hours (8-10am, 4-8pm), excluding Sundays and public holidays. Premium spaces cost AED 6/hour at peak; other paid spaces cost AED 4 — [WAM](https://www.wam.ae/article/biuz2vx-parkin-announces-variable-parking-tariff-policy); [Arabian Business](https://www.arabianbusiness.com/latest-news/dubai-paid-parking-hike-parkin-announces-new-peak-off-peak-rates-in-2025); [DFM filing PDF](https://feeds.dfm.ae/documents/2025/Mar/21/34fa1e71-8c92-4d4f-ad9d-4075fbbfeec9/Variable%20Tariff%20Poli.pdf)
- Effective 4 Apr 2025 per Parkin's filing. Other outlets said end-March or early April. — [DFM filing](https://feeds.dfm.ae/documents/2025/Mar/26/5f368ffd-9677-4134-978e-6523c8d8be6a/Variable%20Parking%20Tar.pdf); [Khaleej Times](https://www.khaleejtimes.com/uae/dubai-parking-code-update-variable-rates)
- Zone-level rates vary by source. Example: Zone B off-street standard AED 4 peak / AED 3 off-peak, Zone D off-street AED 4 / AED 2 — [Yallamotor](https://www.yallamotor.com/news/parking-charges-in-dubai-to-take-effect-from-april-2025-52598)
- Event congestion pricing: AED 25/hour near event venues, starting with DWTC in Feb 2025 — [Gulf News](https://gulfnews.com/amp/story/uae%2Ftransport%2Fexplained-variable-paid-parking-rates-and-congestion-charges-in-dubai-1.104788169)
- Dubai payment channels: Parkin app/website, RTA Dubai app, WhatsApp, SMS to 7275 in the format `<Plate> <Zone> <Hours>`. Reply "Y" to extend. An expiry alert arrives about 10-15 min before end. Parkin app has "PayLater" for multistorey parking (pay within 48 h after exit). — [Gulf News](https://gulfnews.com/living-in-uae/transport/dubais-parkin-app-launches-paylater-feature-pay-for-multistorey-parking-up-to-48-hours-after-exit-1.500045922); [Dubizzle](https://www.dubizzle.com/blog/cars/parkin-app-dubai/); [Emirates 24/7](https://www.emirates247.com/uae/how-to-avoid-parking-fines-in-dubai-9-smart-tips-every-driver-should-know/820)
- Abu Dhabi Mawaqif: standard AED 2/hour (or AED 15 per 24 h), premium AED 3/hour (max 4 h). Reported free on Sundays and public holidays. SMS number is 3009. — [Shory](https://www.shory.com/blog/car-insurance/mawaqif-abu-dhabi-parking-types-fees-and-payment-methods); [Dubizzle](https://www.dubizzle.com/blog/cars/abu-dhabi-sms-parking-payment/)
- Sharjah: SMS to 5566 (not 5155). Each SMS adds a 38-fils surcharge, e.g. AED 2.38 for 1 h. Extension is 1 h by replying "Y". Overstay fine is about AED 100 per one guide. — [Westgate Dubai](https://westgatedubai.com/sharjah-parking-sms-your-ultimate-guide-to-paying-with-5566/); [Trinity Rental](https://trinityrental.com/blog/guide-to-parking-in-sharjah)
- Gulf News reports one app can pay parking across emirates (likely mParking/national) — [Gulf News](https://gulfnews.com/living-in-uae/transport/you-can-pay-for-parking-across-the-uae-through-one-app-1.500200328)
- Parkonic is ticketless at Dubai Silicon Oasis on-street parking (from 1 June 2026 per Gulf News). The default payment is the Salik account unless an SMS session is started. — [Parkonic](https://www.parkonic.com/on-street-parking-silicon-oasis); [Gulf News](https://gulfnews.com/amp/story/uae%2Ftransport%2Fnew-paid-parking-in-dubai-silicon-oasis-from-june-1-timings-and-rates-explained-1.500559371)
- Dubai Pulse "number of parking spaces by zone" open dataset exists but its data is from 2017 — [FCSC Open Data](https://opendata.fcsc.gov.ae/@dubaipulse/number-parking-spaces-zone)

### Inferences
- High-value automatic feature: on Android, parse confirmation SMS from parking shortcodes (7275 / 3009 / 5566) and from Salik/Parkonic debits into parking expenses (amount, zone, duration, plate). On iOS, offer a Shortcuts automation or share-sheet capture.
- Also add an "SMS parking composer": a deep link to `sms:7275?body=<plate> <zone> <hours>` using a stored plate. The cost is logged automatically from the known tariff, which beats parsing.
- Handle the SMS surcharge (Sharjah 38 fils), VAT and per-emirate zone codes in the tariff tables.

### Gaps
- Exact confirmation-SMS text formats and sender IDs were not found.
- RAK, Ajman, Fujairah and UAQ parking details were not researched.
- No public Parkin API was found.

## Registration (Mulkiya), inspection, insurance, traffic fines and black points

### Takeaway
Mulkiya is renewed every 12 months. In Dubai it costs about AED 350 plus an AED 20 Knowledge and Innovation fee for a private light vehicle. Cars over 3 years old need a technical inspection first, about AED 150-170 (re-test AED 50) at Tasjeel/Shamil or ADNOC centres in Abu Dhabi. Insurance must be valid for 12 months (13 in Sharjah). Third-party cover starts around AED 600-640/year; comprehensive is roughly 1.5-3.2% of car value. Fines and black points are checked via Dubai Police, RTA and MOI apps; the federal suspension threshold is 24 points.

### Cited Findings
- Dubai renewal: AED 350 plus AED 20 Knowledge and Innovation fees, 12-month maximum validity. Other guides give totals of AED 350-420. Inspection is AED 150-170, re-inspection AED 50. — [PolicyBazaar](https://www.policybazaar.ae/know-everything-about-car-registration-renewal-in-dubai-ciart/); [CarSwitch](https://carswitch.com/newsroom/rta-mulkiya-renewal-for-used-cars-in-the-uae-2026-requirements-costs-what-can-block-it/)
- Inspection is generally required for vehicles over 3 years old. Tasjeel and Shamil centres do testing and renewal in one visit. — [CarSwitch](https://carswitch.com/newsroom/rta-mulkiya-renewal-for-used-cars-in-the-uae-2026-requirements-costs-what-can-block-it/); [Shory](https://www.shory.com/blog/car-insurance/mulkiya-renewal-process-in-dubai/)
- Abu Dhabi: no AED 20 K&I fee; inspection at ADNOC centres for about AED 100-150; 30-day grace period reported — [PolicyBazaar AD](https://www.policybazaar.ae/car-insurance/articles/car-registration-renewal-in-abu-dhabi/); [Dubizzle](https://www.dubizzle.com/blog/cars/car-registration-renewal-abu-dhabi/)
- Insurance validity: at least 12 months for Dubai renewal, 13 months for Sharjah — [PolicyBazaar Sharjah](https://www.policybazaar.ae/car-insurance/articles/car-registration-renewal-in-sharjah.md); [PolicyBazaar Dubai](https://www.policybazaar.ae/know-everything-about-car-registration-renewal-in-dubai-ciart/)
- Expired Mulkiya: AED 500 fine plus impound risk. Lapsed more than 12 months adds an AED 400 re-registration fee. Unpaid fines block renewal. — [CarSwitch](https://carswitch.com/newsroom/rta-mulkiya-renewal-for-used-cars-in-the-uae-2026-requirements-costs-what-can-block-it/)
- Insurance cost: third-party from about AED 600-640/year. Comprehensive about AED 1,200-1,500 for a sedan, or 1.5-3.2% of car value; luxury/EV AED 3,000-8,000 — [PolicyBazaar](https://www.policybazaar.ae/car-insurance.md); [Gargash](https://gargashinsurance.com/faqs/motor-insurance/types-coverage-benefits/how-much-do-i-need-to-pay-for-third-party-car-insurance-in-the-uae/); [Zigwheels](https://www.zigwheels.ae/car-news/comprehensive-vs-third-party-insurance-in-the-uae-which-should-you-choose)
- Black points are federal and tied to the licence. 24 points within a year means suspension (3-6 months per one guide, up to 1 year per another), and fine discounts do not reduce points — [LookInsure](https://lookinsure.com/blog/dubai-black-points); [Edarabia](https://www.edarabia.com/dubai-police-traffic-fines/)
- The MOI app and moi.gov.ae show the federal points balance across emirates. Dubai Police app uses UAE Pass sign-in. Fine discounts of up to 50% (holiday campaigns) and early-payment discounts (e.g. 35%) exist and vary by emirate — [PayIt](https://payit.ae/blog/uae-traffic-fines-get-a-35-discount-and-reduce-black-points-using-these-payment-channels/); [Khaleej Times](https://www.khaleejtimes.com/uae/transport/50-discount-on-traffic-fines-4-ways-to-pay-which-violations-are-not-covered?amp=1); [Gulf News](https://gulfnews.com/uae/transport/uae-national-day-boost-sharjah-police-announce-limited-time-black-point-removal-offer-1.500361767)
- Fine installment threshold is disputed: AED 500 vs AED 5,000, via partner banks at 0% — [PropertyFinder](https://www.propertyfinder.ae/blog/get-discounts-traffic-fines-uae); [Shory](https://www.shory.com/car-insurance/blog/how-to-get-discounts-on-traffic-fines-in-dubai/)
- Dubai Pulse publishes speed and radar limit data — [Dubai Pulse](https://www.dubaipulse.gov.ae/data/dp-traffic/dp_speed_and_radar_limits-open)

### Inferences
- Core reminders: Mulkiya expiry (the anchor date), with chained tasks 30 days before: inspection (if the car is 3 or more years old), insurance renewal (must cover 12/13 months) and paying fines (a renewal blocker). Insurance and Mulkiya dates are usually aligned.
- Fines: offer manual entry with fields for black points and discount (amount paid vs original). Highlight the early-payment discount window. A deep link to the MOI or Dubai Police app is enough; no API for personal fines was found.
- Model insurance as an annual fixed cost amortized monthly in TCO views.

### Gaps
- Official RTA fee schedule not fetched; exact 2026 fees per emirate and the current inspection-age rule are unverified.
- Current 2026 discount percentages are not confirmed officially.

## Local apps: features and third-party integration potential

### Takeaway
All major UAE mobility and fuel apps (RTA Dubai, Salik, Parkin, TAMM, MOI, Dubai Police, ADNOC, ENOC Pay) are closed consumer apps with no documented public APIs for user data. Practical integration means deep-linking out, parsing SMS, email or receipts, and using open data (Dubai Pulse API with key) for static references. Monthly government fuel prices are easy to automate.

### Cited Findings
- ADNOC Distribution app: mobile fuel payment (MobilePay), ADNOC Wallet top-up, LPG ordering, ADNOC Rewards earn and burn (+25% bonus points when paying in app). Fill & Go uses cameras to recognise a pre-registered plate. Smart Tag RFID installation can be booked in the app. — [ADNOC Distribution](https://www.adnocdistribution.ae/consumer-fuel); [ADNOC Rewards](https://www.adnocdistribution.ae/rewards); [App Store](https://apps.apple.com/app/id1015371099)
- ADNOC Wallet pays across the network via Emirates ID, RFID tag, phone or ADNOC Plus card — [ADNOC Rewards](https://www.adnocdistribution.ae/rewards)
- ENOC Pay app: pay for fuel (by entering site and pump number), VIP RFID top-up and ZOOM/Pronto in-store purchases. It sends transaction notifications and digital receipts and keeps a history. Reviews mention failures and no rewards. — [App Store](https://apps.apple.com/us/app/enoc-pay/id1395158011); [PetrolPlaza](https://www.petrolplaza.com/news/21664)
- ENOC VIP tag: an RFID tag near the fuel inlet with a prepaid account; AED 250 registration per a 2014 report (likely outdated) — [Gulf News 2014](https://gulfnews.com/amp/story/uae%2Ftransport%2Fnow-refuel-your-car-the-vip-way-1.1707845)
- Fuel delivery: CAFU (app-based), and ENOC Link launched as a rival — [Gulf News](https://gulfnews.com/uae/transport/uaes-enoc-launches-fuel-delivery-service-to-rival-cafu-1.66989265)
- Dubai Pulse offers a web API (filters, limits, offsets, column selection). Some datasets need a permission request, and the API key and secret are emailed. Datasets include RTA stations, traffic incidents, speed/radar limits and the Salik tariff (obsolete). A catalog dataset lists all datasets. — [Dubai Pulse catalog](https://www.dubaipulse.gov.ae/data/smartdubai-general/smart_dubai_dubai_pulse_data_catalog_open-open); [Dubai Pulse traffic incidents](https://www.dubaipulse.gov.ae/data/dp-traffic/dp_traffic_incidents-open); [RTA open data](https://rta.ae/wps/portal/rta/ae/home/open-data?lang=en)
- UAE fuel prices are set monthly by the Fuel Price Follow-up Committee, announced on the last day of the month. November 2026: Super 98 AED 4.16, Special 95 AED 4.04, E-Plus 91 AED 3.97, diesel AED 4.75 (October: AED 4.40 / 4.28) — [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced); [Emirates 24/7](https://www.emirates247.com/uae/uae-fuel-prices-for-november-2026-announced/7306?ot=ot.AMPPageLayout)

### Inferences
- Auto-fill the price per litre from the monthly national fuel price table (one price nationwide, same at ADNOC, ENOC and Emarat). The user then enters only AED or litres, and the app derives the other. This is a strong UAE-specific UX win, since retail prices are fixed per grade.
- Receipt capture: ENOC Pay and ADNOC send digital receipts and notifications. Parse forwarded emails, or OCR the receipt screenshot.
- Deep links: open the Salik, Parkin, RTA, MOI and ADNOC apps by store URL or universal link for "pay/top-up" actions. Schemes are undocumented, so fall back to store links.
- Fuel delivery (CAFU/ENOC Link) costs fuel plus a delivery fee. Track it as a fuel entry with a separate service-fee field.

### Gaps
- Emarat app features, ADNOC Rewards earn rate per AED, CAFU 2026 pricing and service-booking apps (Shift etc.) were not found in this research.
- RTA Dubai app and Dubai Drive feature lists, and TAMM vehicle services, were not researched in detail.
- No source confirms any official partner API program for Salik, Parkin, ADNOC or ENOC.

## Localization: AED, Arabic RTL, Hijri, units, common models, summer AC

### Takeaway
Little was sourced directly in this pass. The UAE uses km and litres, and prices are in AED with fils subunits (2 decimals). Fuel is priced monthly per grade (see above). Arabic RTL, the Hijri calendar and Ramadan timetables matter: Salik already switches tariffs during Ramadan.

### Cited Findings
- Ramadan changes toll timetables (Salik 2026), which shows the calendar matters for tariffs — [ArabWheels](https://www.arabwheels.ae/blog/salik-toll-changes-ramadan-2026-dubai/)
- Grade naming in the UAE: Super 98, Special 95, E-Plus 91, Diesel — [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced)
- Sundays are free or reduced for Darb, Mawaqif and Parkin, and Salik has a flat Sunday rate. The UAE weekend is Sat-Sun, but the transport "off day" is Sunday. — [Selfdrive.ae](https://selfdrive.ae/blog/dubai-salik-charges-explained-how-much-will-you-pay-in-2026); [WAM](https://www.wam.ae/article/biuz2vx-parkin-announces-variable-parking-tariff-policy)

### Inferences
- Format currency with `Intl.NumberFormat('ar-AE'|'en-AE', {style:'currency', currency:'AED'})`. Default to 2 decimals, because Sharjah SMS charges like AED 2.38 and VAT values like 4.20 need fils. Offer Western or Eastern Arabic digits.
- Support RTL mirroring. Offer the Islamic (Umm al-Qura) calendar as a secondary display (`ar-AE-u-ca-islamic-umalqura`). Keep the Gregorian calendar as primary, because all government fees and expiries are Gregorian.
- Default to km, L, L/100km and km/L; UAE drivers commonly use km/L or L/100km.
- Benchmarks: large SUVs (Nissan Patrol, Toyota Land Cruiser) and sedans (Nissan Sunny, Toyota Camry/Corolla) are common. Seed manufacturer combined figures as benchmarks, then compare the user's actual consumption against them.
- Summer AC use (ambient often above 45C) likely raises consumption noticeably. Show seasonal (Jun-Sep vs Nov-Mar) efficiency comparisons rather than claiming a specific percentage.

### Gaps
- No sourced data on UAE best-selling models, typical real-world Patrol/Land Cruiser L/100km, or measured summer AC fuel penalty in UAE conditions.
- No sourced guidance on Hijri usage preferences among UAE users.

## Privacy/legal: UAE PDPL (Federal Decree-Law 45/2021), location and trip data, residency

### Takeaway
The PDPL has applied since 2 Jan 2022 and is GDPR-like: it needs a lawful basis with explicit, specific consent, and Articles 22-23 govern cross-border transfers (adequacy or contractual safeguards). Its Executive Regulations, including penalties, were still reported as pending in 2026. There is no general data-localisation rule for a consumer vehicle app; localisation is sector-specific (health, finance). DIFC and ADGM have separate regimes.

### Cited Findings
- Effective 2 Jan 2022. Controllers have 6 months after the Executive Regulations are issued to comply. Penalties are left to the Executive Regulations. — [HSF Kramer](https://www.hsfkramer.com/insights/2021-12/new-data-protection-law-introduced-in-the-uae-%E2%80%93-10-key-takeaways); [Bird & Bird](https://www.twobirds.com/en/insights/2021/uae/how-does-the-new-uae-federal-decree-law-on-personal-data-protection-compare-against-the-gdpr)
- Executive Regulations status: reported as still pending as of 2026 — [ConsentStack](https://consentstack.io/regulations/uae-pdpl); contradicted by a timeline claiming March 2022 publication — [Clym](https://www.clym.io/regulations/federal-decree-law-no-45-united-arab-emirates) (the latter appears unreliable)
- Cross-border transfers (Arts 22-23) are allowed to adequate jurisdictions, or otherwise via contracts and other routes. No published adequacy list from the UAE Data Office as of early 2026. In practice, contractual clauses are used. — [Clifford Chance](https://www.cliffordchance.com/insights/resources/blogs/talking-tech/en/articles/2025/03/doing-business-in-the-middle-east--data-transfers-in-the-uae-and-ksa.html); [Kayrouz & Associates](https://www.kayrouzandassociates.com/insights/cross-border-data-transfers-under-uae-law-in-2026); [trade.gov](https://www.trade.gov/market-intelligence/united-arab-emirates-allows-cross-border-data-flows-personal-data)
- Consent must be explicit and specific; bundled consent is invalid — [Kayrouz & Associates](https://www.kayrouzandassociates.com/insights/cross-border-data-transfers-under-uae-law-in-2026)
- Localisation is sector-specific: health data under Federal Law No. 2 of 2019, and financial data under the CBUAE Consumer Protection Standards — [Middle East Briefing](https://www.middleeastbriefing.com/news/uae-data-protection-obligations-and-cross-border-data-transfer-for-businesses/)
- PDPL does not apply in DIFC or ADGM, which have their own data-protection laws — [Bird & Bird](https://www.twobirds.com/en/insights/2021/uae/how-does-the-new-uae-federal-decree-law-on-personal-data-protection-compare-against-the-gdpr)

### Inferences
- Location traces reveal home and work locations. Treat GPS trips and SMS contents as high-sensitivity data. Use opt-in toggles per feature (toll estimation, SMS parsing), a purpose-limited and processed-on-device design (parse SMS locally and store only the extracted amount, date and type), retention controls, export and deletion.
- Local-first storage, with optional cloud sync hosted in a UAE region (Azure UAE North, AWS me-central-1 UAE, Oracle/G42 local), avoids transfer ambiguity while there is no adequacy list. This is not legally required for a non-health, non-finance app, but it is a selling point.
- Avoid storing Emirates ID numbers; plate numbers suffice for SMS parking. Payment card data falls under PCI and CBUAE rules, so do not store it.

### Gaps
- No source specifically on PDPL treatment of geolocation or vehicle telematics data.
- Final Executive Regulations and the penalty regime are unconfirmed as of Nov 2026.
- No source checked whether a UAE Data Office registration or DPO is required for a small consumer app.
