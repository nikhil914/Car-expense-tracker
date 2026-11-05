# UAE Fuel Prices and UAE-Only Route Cost Estimator (as of 5 Nov 2026)

Research note: WebFetch failed for every host tried (DNS blocked): khaleejtimes.com, gulfnews.com, carsclub.ae, blog.oneclickdrive.com. All findings below come from WebSearch result summaries and snippets. Full article pages were not read, so verify the figures against the primary announcements (WAM / Fuel Price Committee) before they go into production data.

## 1. How the UAE Fuel Price Committee sets prices, and when and where prices are announced

### Takeaway
Since August 2015 the UAE has linked retail fuel prices to international markets. A national Fuel Price Committee resets prices once a month. The committee announces new prices on the last day of each month (e.g., 30 Sep, 31 Dec, 28 Feb), and they take effect on the 1st of the next month. WAM (the state news agency) carries the announcement, and Gulf News, Khaleej Times, The National, Aletihad and Emirates 24|7 republish it within hours.

### Cited Findings
- The UAE has tied domestic pump prices to international markets since 2015, and a committee resets them monthly — [Arabian Business](https://www.arabianbusiness.com/abnews/uae-announces-new-fuel-prices-for-april-2026)
- Prices are set from average international crude and refined-fuel costs — [Gulf News, Apr 2026](https://gulfnews.com/business/energy/uae-announces-petrol-and-diesel-prices-for-april-2026-1.500491487)
- The committee announces new prices on the last day of each month, effective from the 1st of the following month — [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html)
- October 2026 prices were approved on 30 September and took effect 1 October. One outlet names the body "Gasoline and Diesel Prices Follow-up Committee" — [Emirates 24|7](https://www.emirates247.com/uae/uae-fuel-prices-for-october-2026-announced/6171); [Oil & Gas Middle East](https://www.oilandgasmiddleeast.com/news/uae-fuel-prices-october)
- Announcement dates seen in 2026: 31 Dec 2025 (for Jan), 31 Jan (for Feb), 28 Feb (for Mar), 30 Sep (for Oct) — [The National, Dec 31 2025](https://www.thenationalnews.com/business/energy/2025/12/31/uae-petrol-price-january-2026); [The National, Jan 31 2026](https://www.thenationalnews.com/business/energy/2026/01/31/uae-fuel-prices-to-fall-in-february); [Gulf Today, Jan 31 2026](https://www.gulftoday.ae/news/2026/01/31/fuel-prices-from-february-2026-in-uae-announced); [uaebarq, Sep 30 2026](https://www.uaebarq.ae/en/2026/09/30/uae-fuel-prices-for-october-2026-announced/)
- WAM publishes the committee release ("UAE Fuel Price Committee announces prices for …"), and Aletihad syndicates it with a WAM dateline — [WAM](https://www.wam.ae/en/article/178ypmj-uae-fuel-price-committee-announces-prices-for); [Aletihad (WAM), March 2026](https://en.aletihad.ae/amp/news/business/4648017/uae-fuel-price-committee-announces-prices-for-march)
- Some outlets publish a "prices set to rise/fall" forecast a few days before the announcement, based on the month's global oil moves — [The National, Feb 28 2026](https://www.thenationalnews.com/news/uae/2026/02/28/uae-fuel-prices-set-to-rise-in-march); [Hit 96.7 "fuel prices to drop in November"](https://www.hit967.ae/news/local-news/uae-fuel-prices-to-drop-in-november1)
- ADNOC Distribution's live price page lagged the new April 2026 price: it still showed Super 98 at AED 2.59 after the change — [Arabian Business](https://www.arabianbusiness.com/business/retail/uae-petrol-prices-will-change-this-week)

### Inferences
- The ingestion trigger should run on the last calendar day of the month in the evening (Gulf Standard Time) and retry until the 1st. Push a notification to users on the evening of the last day or the morning of the 1st.
- WAM is the most authoritative machine-readable source: one release per month with a predictable title pattern. Retailer pages (ADNOC) can lag, so do not treat them as the primary source.
- The exact formula (benchmark index, margins) is not public in any source found. The app can only approximate a "next-month forecast" from global benchmark moves, for example Platts/Brent month-average change versus the previous month. Label any such forecast clearly as an estimate.

### Gaps
- No primary source found that sets out the committee's membership, its formula, or the Ministry of Energy & Infrastructure (moei.gov.ae) page structure. Fetches were blocked, so I could not confirm whether MOEI posts prices on its own site.
- No confirmation found of the role of ENOC/Emarat press releases as announcement channels.

## 2. Fuel grades and monthly price history (AED/litre)

### Takeaway
There are four grades: Super 98, Special 95, E-Plus 91 and Diesel. 2026 has been volatile. Prices fell to about AED 2.45 for Super 98 in Feb 2026. They jumped about 80 fils in April 2026, when the US-Israel-Iran war pushed oil higher. They peaked in Oct 2026 (Super 98 AED 4.40, Diesel AED 4.80) and eased in Nov 2026 (Super 98 AED 4.16). I found no official open API or community GitHub dataset. The app will need to build its own dataset: scrape WAM/news, with an admin override.

### Cited Findings — price table (AED/litre)

| Month | Super 98 | Special 95 | E-Plus 91 | Diesel | Source |
|---|---|---|---|---|---|
| Nov 2025 | 2.63 | 2.51 | 2.44 | 2.67 | [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html) |
| Dec 2025 | 2.70 | 2.58 | 2.51 | 2.85 | [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html); [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates) |
| Jan 2026 | 2.53 | 2.42 | 2.34 | 2.55 | [Khaleej Times](https://www.khaleejtimes.com/petrol-prices-january-2026); [Gulf News](https://gulfnews.com/business/energy/uae-petrol-diesel-prices-for-january-2026-announced-1.500393811) |
| Feb 2026 | 2.45 | 2.33 | 2.26 | 2.52 | [Gulf Today](https://www.gulftoday.ae/news/2026/01/31/fuel-prices-from-february-2026-in-uae-announced); [Khaleej Times](https://www.khaleejtimes.com/uae/petrol-prices-february-2026) |
| Mar 2026 | 2.59 | 2.48 | 2.40 | 2.72 | [Khaleej Times](https://www.khaleejtimes.com/energy/uae-petrol-prices-march-2026); [Aletihad/WAM](https://en.aletihad.ae/amp/news/business/4648017/uae-fuel-price-committee-announces-prices-for-march) |
| Apr 2026 | 3.39 | 3.28 | 3.20 | 4.69 | [Gulf News](https://gulfnews.com/business/energy/uae-announces-petrol-and-diesel-prices-for-april-2026-1.500491487); [Arabian Business](https://www.arabianbusiness.com/abnews/uae-announces-new-fuel-prices-for-april-2026) |
| May 2026 | 3.66 | n/f | 3.48 | 4.69 | [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates); [Arabian Business](https://www.arabianbusiness.com/business/retail/uae-petrol-prices-will-change-this-week) |
| Jun 2026 | 3.95 | n/f | n/f | 4.33 | [Dubai Fast Living](https://dubaifastliving.com/uae-fuel-prices-june-2026/); [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates) |
| Jul 2026 | 3.40 | n/f | n/f | 3.60 | [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates); [Arabian Business](https://www.arabianbusiness.com/business/retail/uae-petrol-prices-will-change-this-week) |
| Aug 2026 | 3.60 | 3.49 | 3.41 | 3.80 | [Yallamotor](https://www.yallamotor.com/news/uae-fuel-prices-october-2026-analysis-55857); [Emirates 24|7](https://www.emirates247.com/uae/uae-fuel-prices-for-october-2026-announced/6171) |
| Sep 2026 | 3.80 | 3.69 | 3.61 | 4.30 | [Yallamotor](https://www.yallamotor.com/news/uae-fuel-prices-october-2026-analysis-55857); [Tire.ae](https://www.tire.ae/en/blogs/latest-petrol-prices-uae) |
| Oct 2026 | 4.40 | 4.28 | 4.21 | 4.80 | [Emirates 24|7](https://www.emirates247.com/uae/uae-fuel-prices-for-october-2026-announced/6171); [Gulf News](https://gulfnews.com/business/energy/uae-petrol-diesel-prices-for-october-2026-announced-1.500692323) |
| Nov 2026 | 4.16 | 4.04 | 3.97 | 4.75 | [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced); [Oil & Gas ME](https://www.oilandgasmiddleeast.com/news/uae-fuel-prices-november); [Dubicars](https://www.dubicars.com/news/petrol-price-in-uae.html) |

n/f = not found in search results.

### Cited Findings — context
- April 2026 jump: each petrol grade rose about 80 fils, and diesel rose AED 1.97 (2.72 to 4.69). The driver was higher oil prices during the regional conflict / US-Israel-Iran war — [Arabian Business](https://www.arabianbusiness.com/abnews/uae-announces-new-fuel-prices-for-april-2026); [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-april-2026)
- Secondary sites tie the surge to disrupted Strait of Hormuz flows. Official coverage cites global markets and the conflict, not Hormuz by name — [Dubai Fast Living](https://dubaifastliving.com/uae-fuel-prices-june-2026/)
- Discrepancy: EZHire lists April diesel at AED 4.62 against 4.69 elsewhere — [EZHire](https://www.ezhire.ae/blog/petrol-price-in-the-uae)
- July 2026: petrol fell about AED 0.55 and diesel about AED 0.73 — [Arabian Business](https://www.arabianbusiness.com/business/retail/uae-petrol-prices-will-change-this-week)
- October 2026 was the third straight monthly rise and the 2026 high. Special 95 rose about 16%, E-Plus 91 16.6% (3.61 to 4.21) and diesel 11.6% (4.30 to 4.80) — [Oil & Gas ME](https://www.oilandgasmiddleeast.com/news/uae-fuel-prices-october); [Gulf News](https://gulfnews.com/business/energy/uae-petrol-diesel-prices-for-october-2026-announced-1.500692323)
- November 2026: Super 98 fell 24 fils to 4.16 and diesel fell 5 fils to 4.75, effective 1 Nov — [Oil & Gas ME](https://www.oilandgasmiddleeast.com/news/uae-fuel-prices-november); [Khaleej Times](https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced)
- 2025 pattern (Gulf News history page snippet): prices climbed through mid-2025, peaked around October and eased into December — [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates)

### Cited Findings — datasets and APIs
- CEIC has monthly AED/l series sourced from the Ministry of Energy, Aug 2015 to Dec 2018 (41 observations; diesel median 1.95). It is paywalled and stale — [CEIC](https://ceicdata.com/en/united-arab-emirates/gasoline-and-diesel-prices)
- OilPriceAPI exposes UAE retail codes such as `DIESEL_RETAIL_AE_AED`. Its diesel page showed "Data unavailable" when checked — [OilPriceAPI diesel](https://www.oilpriceapi.com/diesel-prices/middle-east/uae); [OilPriceAPI gasoline](https://www.oilpriceapi.com/gasoline-prices/middle-east/uae)
- GlobalPetrolPrices publishes weekly UAE gasoline prices in a single grade (95). Bulk data is paid — [GlobalPetrolPrices](https://www.globalpetrolprices.com/United-Arab-Emirates/gasoline_prices/)
- TradingEconomics carries a UAE gasoline price series — [TradingEconomics](https://tradingeconomics.com/united-arab-emirates/gasoline-prices)
- Gulf News keeps a historical fuel-rates page, a likely scrape target for history — [Gulf News history](https://gulfnews.com/gold-forex/historical-fuel-rates)
- Hobby trackers exist (fuelpricesuae.com 6-month trend, fuelpriceuae.com, fares.net) — [fuelpricesuae.com](https://fuelpricesuae.com/price-history/); [fares.net](https://fares.net/prkn/fuel/?lang=en)
- No GitHub repo or data.gov.ae / Bayanat dataset surfaced in search — [search summary; no source]

### Inferences
- Recommended ingestion pipeline:
  1. Run a cron job from the last day of the month, 16:00 GST, polling WAM English (wam.ae) for a title matching "Fuel Price Committee announces prices for {Month}".
  2. As fallback, parse Gulf News / Khaleej Times headlines ("UAE petrol, diesel prices for {Month YYYY} announced").
  3. Regex-extract the four "AED x.xx per litre" values. Sanity-check each against the previous month, for example flag any change greater than AED 2.00, since April 2026 diesel moved AED 1.97.
  4. Queue for admin approval, or auto-publish if two sources agree.
  5. Send a push notification.
  Keep a manual admin entry screen as the final fallback.
- Seed history 2015–2025 by back-scraping Gulf News/Khaleej Times monthly articles (URL pattern `uae-petrol-diesel-prices-for-{month}-{year}-announced`). Then verify against WAM.
- The data model needs `effective_month (YYYY-MM)`, `grade`, `aed_per_litre`, `announced_at` and `source_url`. Price lookup for a trip uses `effective_month = month(trip_date)`. A future month uses either the latest price or the forecast.

### Gaps
- Missing: May–Jul 2026 Special 95/E-Plus 91 (except May E-Plus 3.48), all of Jan–Oct 2025, and all of 2015–2024. Fetches were blocked, so full tables were not retrieved.
- From prior knowledge only (unverified in this session): 2022 peaked in July 2022, with Super 98 at about AED 4.63 and diesel at about AED 4.76. The pre-deregulation price was about AED 1.72 (Super 98) in July 2015. Do not use these without verification.
- November 2026 figures appear in several outlets (Khaleej Times, Oil & Gas ME, Dubicars, Dubai Fast Living). The WAM primary release was not seen.

## 3. Uniformity across emirates and retailers; service fees

### Takeaway
Prices are set nationally, and every major outlet reports one UAE-wide price per grade. I found no evidence of emirate-specific or brand-specific (ADNOC/ENOC/Emarat) differences, but no source states uniformity explicitly either. ADNOC introduced an optional AED 10 attendant fee ("Flex") in 2018 and extended it to the Northern Emirates. Later reports say ADNOC scrapped the fee, but the current status is unclear.

### Cited Findings
- From 30 June 2018, full-service fill-ups at ADNOC Abu Dhabi stations cost an extra AED 10. Self-service is free, and elderly and disabled drivers are exempt — [Arabian Business](https://www.arabianbusiness.com/industries/energy/399536-adnoc-customers-must-pay-aed-10-or-pump-own-petrol-from-june-30); [Gulf News](https://gulfnews.com/uae/transport/adnoc-launches-flex-pay-dh10-for-assistance-at-petrol-stations-in-abu-dhabi-1.2242698)
- Flex was extended from 21 October (2018) to Sharjah, RAK, Fujairah, UAQ and Ajman — [Arabian Business](https://www.arabianbusiness.com/industries/retail/406366-adnoc-says-to-expand-flex-service-to-northern-emirates)
- Later report: ADNOC "will not charge Dh10 to fuel vehicles" (date not in snippet) — [Dubai Eye](https://dubaieye1038.com/news/local/adnoc-will-not-charge-dh10-to-fuel-vehicles); [Lovin Dubai (fee abolished at certain times)](https://lovin.co/dubai/en/news/petrol-pump-charge-dubai)

### Inferences
- Use one national price per grade per month. Add an optional "service fee" toggle (default AED 0) rather than modelling brand prices.

### Gaps
- No source explicitly confirms identical pump prices at ENOC/Emarat across all emirates. I also could not confirm whether the ADNOC AED 10 fee is in force in Nov 2026.

## 4. EV charging tariffs

### Takeaway
A de facto national public rate exists: AC AED 0.70/kWh (0.735 incl. VAT) and DC AED 1.20/kWh (1.26 incl. VAT). DEWA, UAEV, ADNOC E2GO and Tesla Superchargers all use it. The sources are mostly secondary, so verify in each network's app.

### Cited Findings
- DEWA Green Charger: AC AED 0.735/kWh and DC AED 1.26/kWh incl. VAT (base 0.70/1.20 + 5% VAT) — [ZigWheels DEWA guide 2026](https://www.zigwheels.ae/car-news/dewa-green-charger-guide-2026-how-to-charge-your-ev-in-dubai); [YallaMotor GCC EV guide](https://www.yallamotor.com/news/gcc-ev-public-charging-guide-networks-tariffs-apps-dubai-riyadh-55557)
- Older sources still cite DEWA 29 fils/kWh for registered users. This appears outdated — [Dubizzle](https://www.dubizzle.com/blog/cars/dewa-ev-green-chargers/)
- One source claims a free first year for new DEWA registrants (Feb 2026 announcement). This is unconfirmed — [search summary of ZigWheels/evlife](https://evlife.world/ae/in-uae/ev-charging-in-the-uae-your-full-guide/)
- UAEV (federal network): AC 0.70 (0.735 incl. VAT), DC 1.20 (1.26 incl. VAT). Pay-as-you-go app, no deposit — [Plaidcars EV map](https://www.plaidcars.com/ev-charger-map/#abu-dhabi); [ZigWheels tariff comparison](https://www.zigwheels.ae/car-news/latest-ev-charger-tariffs-in-uae-new-cost-comparison-across-public-stations)
- Tesla Supercharger UAE: AED 1.26/kWh flat, no peak pricing (checked Oct 2026). Idle fees run up to AED 4/min. Charging was free in Sharjah/Ajman/RAK/Fujairah until about March 2026 — [Plaidcars](https://www.plaidcars.com/tesla/tesla-charging-cost-uae/); [TikTok, Nov 2024](https://www.tiktok.com/@iamrezmo/video/7435609675036380471)
- ADNOC E2GO: billed per kWh in the ADNOC app, at or above AED 1.26 DC. Launch phase was free, and every 4th session is complimentary — [Plaidcars](https://www.plaidcars.com/ev-charger-map/#abu-dhabi)
- Abu Dhabi historical: 30 fils/kWh at home/sub-metered units (2021) — [Gulf Business](https://gulfbusiness.com/en/2021/abu-dhabi/abu-dhabi-introduces-unified-tariff-for-electric-vehicle-charging-across-emirate)
- Khaleej Times reported unified EV charging prices, with motorists bracing for extra costs — [Khaleej Times](https://khaleejtimes.com/auto/business/ev-charging-prices-unified-uae-motorists-brace-for-extra-costs-but-say-it-will-reduce-crowding?amp=1)

### Inferences
- EV trip cost = distance_km × kWh/100km ÷ 100 × tariff. The tariff options are DC 1.26, AC 0.735, or a home rate the user enters (DEWA residential slabs, not researched here).

### Gaps
- No official DEWA, UAEV, ADNOC or Tesla tariff page was read. The exact current E2GO price above the 1.26 floor is not confirmed.

## 5. Routing APIs, tolls (Salik/Darb), cost formula, and existing apps

### Takeaway
Google Routes API covers the UAE well, but Salik/Darb toll pricing could not be confirmed in its toll feature. Plan to compute tolls yourself: store the gate coordinates, intersect them with the route polyline, and apply time-of-day rules. Mapbox, HERE and self-hosted OSRM/Valhalla on OSM data give cheaper or offline distance and polylines. Salik since June 2026: AED 6.30 peak, AED 4.20 off-peak/Sunday, free 01:00–06:00. Darb: AED 4 per peak crossing, caps removed Sept 2025, and two 24/7 E11 gates added in May 2026.

### Cited Findings — routing APIs
- Google Routes API supports toll estimates via `extraComputations: TOLLS` plus `routeModifiers` (vehicle info, `tollPasses`). Supported passes are listed in the TollPass reference. Coverage is per country on the coverage page — [Google: Calculate toll fees](https://developers.google.com/maps/documentation/routes/calculate_toll_fees?hl=en); [Routes API coverage](https://developers.google.com/maps/documentation/routes/coverage)
- A Google Cloud Community thread from a Dubai ride-hailing developer asked Google to enable and identify Dubai toll gates. This suggests UAE toll support was not available to that developer — [Google Cloud Community](https://www.googlecloudcommunity.com/gc/Developer-Tools/Request-for-Enabling-and-Identifying-Toll-Gates-in-Dubai-UAE-for/td-p/871835)
- Google pricing: Compute Routes Essentials has 10,000 free events/month, then $5.00/1,000. Pro has 5,000 free, then $10/1,000. Enterprise has 1,000 free, then $15/1,000. Toll computation pushes the request into a higher SKU — [gotime pricing notes](https://gotime.readthedocs.io/en/stable/pricing.html); [Google legacy SKU pricing](https://developers.google.com/maps/billing-and-pricing/legacy-sku-pricing?authuser=0); [Google pricing list](https://developers.google.cn/maps/billing-and-pricing/pricing?hl=en)
- Mapbox Directions: first 100,000 requests/month free, then about $2.00/1,000 (100k–500k). Sources conflict — [StoreRocket](https://storerocket.io/learn/mapbox-pricing); [vibereference](https://vibereference.vercel.app/backend-and-data/maps-geocoding-apis)
- HERE Routing v8: free tier reported as 30k/month (Freemium) or 250k/month. Sources conflict, and HERE does not publish per-SKU rates publicly — [DEV Community](https://dev.to/agenthustler/google-maps-api-too-expensive-5-cheaper-alternatives-for-location-data-in-2026-275p); [vibereference](https://vibereference.vercel.app/backend-and-data/maps-geocoding-apis)

### Cited Findings — tolls
- Salik variable pricing started 31 Jan 2025: AED 6 peak (06:00–10:00, 16:00–20:00, Mon–Sat), AED 4 off-peak, free 01:00–06:00 — [CITTI Magazine](https://www.cittimagazine.co.uk/news/road-user-charging-tolling/salik-introduces-variable-toll-pricing-to-optimise-traffic-flow-in-dubai.html); [Platinumlist](https://platinumlist.net/guide/new-salik-toll-gate-pricing-in-dubai-free-hours-and-peak-charges-explained/)
- 5% VAT on Salik from 1 Jun 2026: AED 6.30 peak and AED 4.20 off-peak/Sunday. The free window carries no VAT — [YallaMotor](https://www.yallamotor.com/news/salik-vat-uae-2026-5-percent-tolls-parking-55390); [ZigWheels](https://www.zigwheels.ae/car-news/dubai-motorists-to-pay-more-from-june-1-check-new-salik-parking-charges)
- Ramadan 2026 used a special Salik schedule (peak 09:00–17:00 Mon–Sat, reduced iftar fees). Holiday schedules also change, e.g., Eid Al Adha — [The National](https://www.thenationalnews.com/news/uae/2026/02/16/ramadan-2026-dubais-salik-to-reduce-toll-fees-for-iftar); [Khaleej Times](https://www.khaleejtimes.com/uae/dubai-salik-toll-pricing-eid-al-adha-holiday?amp=1)
- Salik gate count is cited as 8 in one older guide. More gates have been added since, so the gate list needs a current source — [uaecalc](https://uaecalc.pages.dev/salik)
- Darb (Abu Dhabi): AED 4 per crossing at peak (07:00–09:00 and 15:00–19:00 Mon–Sat; evening extended 2h from 1 Sep 2025). Daily/monthly caps (AED 16/day; AED 200/150/100/month) were removed 1 Sep 2025 — [Virgin Radio](https://www.virginradiodubai.com/trending/uae/abu-dhabi-announces-changes-to-darb-toll-timings-and-limits/); [Property Finder](https://www.propertyfinder.ae/blog/darb-toll-gate). Contradicted by [Shory 2026](https://www.shory.com/car-insurance/blog/darb-timing/) and the [ITC caps page](https://itc.gov.ae/en/darb-daily-and-monthly-caps), which still list caps
- New Darb gates at Ghantoot and Al Qurm on E11 (Abu Dhabi–Dubai), live 4 May 2026 and charging 24/7 — [Shory/search summary](https://www.shory.com/car-insurance/blog/new-darb-toll-gates-in-abu-dhabi-locations-timings-charges-guide/)
- Residents report that tolls plus parking can cost more than fuel — [Gulf News](https://gulfnews.com/living-in-uae/transport/uae-residents-say-tolls-and-parking-fees-cost-more-than-fuel-heres-how-they-save-money-1.500193210)

### Cited Findings — existing apps and tools
- JobxDubai commute calculator: compares car/metro/bus/taxi cost and includes Salik, fuel prices and Nol fares — [JobxDubai](https://jobxdubai.com/tools/commute-calculator)
- uaecalc Salik calculator: pick gates and get daily/monthly/annual toll cost — [uaecalc](https://uaecalc.pages.dev/salik)
- OpenVan UAE fuel-price and route fuel-cost tool (EUR, aimed at motorhomes) — [OpenVan](https://openvan.camp/en/tools/fuel-prices/uae)
- fares.net shows UAE fuel and parking info — [fares.net](https://fares.net/prkn/?lang=en)
- Generic trip fuel calculators on the App Store are not UAE-specific — [App Store](https://apps.apple.com/us/app/id1222880998)
- Dubai–Abu Dhabi estimate: 7–10 L one-way, AED 45–60 round trip for an efficient car (at older prices) — [selfdrive.ae](https://selfdrive.ae/blog/driving-from-dubai-to-abu-dhabi-toll-costs-fuel-expenses--road-tips-)

### Inferences
- Cost formula:
  `fuel_cost = distance_km × (L_per_100km / 100) × price[grade][month(trip_date)]`
  `tolls = Σ salik_gate(time, day, holiday_schedule) + Σ darb_gate(time, day)`
  `total = fuel_cost + tolls + parking_estimate + optional_service_fee`
  - If the trip month has no published price, use the latest price, flagged "provisional". Optionally show a forecast: latest price × (1 + Δ month-average benchmark).
- Toll engine: keep gate geofences in a table (lat/lon, direction, operator, schedule, effective dates). Snap the route polyline from any provider. Count crossings, respecting direction. This removes dependence on Google toll data and works with OSRM/Valhalla offline.
- Provider choice: Google gives the best ETA/traffic. Mapbox has the largest free tier. Self-hosted OSRM on the Geofabrik GCC extract has zero marginal cost and works offline/on-device. Apple MapKit (iOS) has no per-request fee within quota.
- Parking: per-emirate hourly tariffs (Dubai Parkin zones, Abu Dhabi Mawaqif, Sharjah) need separate research. Dubai raised parking charges around June 2026 per [ZigWheels](https://www.zigwheels.ae/car-news/dubai-motorists-to-pay-more-from-june-1-check-new-salik-parking-charges).
- I found no app that combines live monthly fuel price, route, Salik/Darb and parking for UAE-wide trips. This looks like a market gap, though the search was limited.

### Gaps
- Not verified: whether the Google Routes TollPass list includes Salik/Darb, and whether UAE shows "toll calculation support" on the coverage page. Mapbox, HERE and Apple UAE toll support were not researched. Official current Salik gate list (count/locations) not retrieved. Darb cap status conflicts. Exact parking tariffs not researched.
