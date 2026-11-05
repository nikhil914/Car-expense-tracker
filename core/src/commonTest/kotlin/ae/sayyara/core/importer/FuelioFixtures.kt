package ae.sayyara.core.importer

/** Fuelio CSV fixtures. SAMPLE_MILES and SAMPLE_KM are the real public samples from the research notes. */
object FuelioFixtures {

    /** tigattack/lubelogger-fuelio-importer fuelio_backup_sample.csv (2024, UK, miles + litres), category list trimmed. */
    val SAMPLE_MILES = """
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
""".trimIndent()

    /** dundee/fuelio2fuelly example-fuelio.csv (2018, Czech, km + litres, newest-first). */
    val SAMPLE_KM = """
"## Vehicle"
"Name","Description","DistUnit","FuelUnit","ConsumptionUnit","ImportCSVDateFormat","VIN","Insurance","Plate","Make","Model","Year","TankCount","Tank1Type","Tank2Type","Active","Tank1Capacity","Tank2Capacity"
"Toyota Auris","","0","0","0","yyyy-MM-dd","","","","toyota","auris","2018","1","100","0","1","50.0","0.0"
"## Log"
"Data","Odo (km)","Fuel (litres)","Full","Price (optional)","l/100km (optional)","latitude (optional)","longitude (optional)","City (optional)","Notes (optional)","Missed","TankNumber","FuelType","VolumePrice","StationID (optional)","ExcludeDistance","UniqueId","TankCalc"
"2018-10-07","424","33.04","1","1172.92","0.0","50.0436","14.4406","Praha, Olbrachtova - Mol","","0","1","0","35.5","329285","0","105","0.0"
"2018-09-17","5","48.02","1","1675.9","0.0","50.0165","14.4026","Hlavní město Praha, Československého exilu - OMV","","0","1","0","34.9","107548","0","103","0.0"
""".trimIndent()

    /** Older Fuelio import layout: comma-padded markers, no spaces in headers, dd.MM.yyyy, no Price column. */
    val OLD_LAYOUT = """
## Vehicle,,,,,,,,,,,,,,,
Name,Description,DistUnit,FuelUnit,ConsumptionUnit,ImportCSVDateFormat,VIN,Insurance,Plate,Make,Model,Year,TankCount,Tank1Type,Tank2Type,Active
Patrol,,0,0,0,dd.MM.yyyy,,,D 12345,Nissan,Patrol,2021,1,112,0,1
## Log,,,,,,,,,,,,,,,
Data,Odo(km),Fuel(litres),Full,Price(optional),l/100km(optional),latitude(optional),longitude(optional),City(optional),Notes(optional),Missed,TankNumber,FuelType,VolumePrice
15.02.2026,10500,70.00,1.0,,0,0,0,ADNOC Al Barsha,,0,1,112,2.59
01.03.2026,11100,40.00,0,,0,0,0,ENOC SZR,"partial, rush",1.0,1,110,2.50
""".trimIndent()

    /** US gallons + miles, with BOM and CRLF line endings. */
    val GALLONS_US = "﻿" + listOf(
        "\"## Vehicle\"",
        "\"Name\",\"DistUnit\",\"FuelUnit\",\"ConsumptionUnit\",\"ImportCSVDateFormat\",\"TankCount\",\"Tank1Type\",\"Tank1Capacity\"",
        "\"F-150\",\"1\",\"1\",\"1\",\"yyyy-MM-dd\",\"1\",\"201\",\"26.0\"",
        "\"## Log\"",
        "\"Data\",\"Odo (mi)\",\"Fuel (gal)\",\"Full\",\"Price (optional)\",\"mpg (optional)\",\"Missed\",\"TankNumber\",\"FuelType\",\"VolumePrice\"",
        "\"2025-06-01 08:00\",\"1000\",\"10.0\",\"1\",\"35.00\",\"0\",\"0\",\"1\",\"201\",\"\"",
        "\"2025-06-10 08:00\",\"1300\",\"12.0\",\"1\",\"\",\"0\",\"0\",\"1\",\"201\",\"3.60\"",
    ).joinToString("\r\n")

    /** Dual tank (petrol + LPG) and costs with templates, income, a clamped date and repeats. */
    val DUAL_TANK = """
"## Vehicle"
"Name","DistUnit","FuelUnit","ImportCSVDateFormat","TankCount","Tank1Type","Tank2Type","Tank1Capacity","Tank2Capacity","FuelUnitTank2"
"Bi-fuel","0","0","yyyy-MM-dd","2","110","401","60","40","0"
"## Log"
"Data","Odo (km)","Fuel (litres)","Full","Price (optional)","TankNumber","FuelType","VolumePrice"
"2026-01-02","2000","30","1","80","2","401",""
"2026-01-01","1500","50","1","130","1","110","2.60"
"## CostCategories"
"CostTypeID","Name","priority"
"5","Parking","0"
"7","Tolls","0"
"8","Tickets","0"
"31","Car Insurance","0"
"50","Ride share","0"
"## Costs"
"CostTitle","Date","Odo","CostTypeID","Notes","Cost","flag","idR","read","RemindOdo","RemindDate","isTemplate","RepeatOdo","RepeatMonths","isIncome","UniqueId"
"Salik top-up","2026-09-31 10:00","2100","7","","50.0","0","0","0","0","","0","0","0","0","1"
"Mall parking","2026-01-05","0","5","","12.5","0","0","0","0","","0","0","0","0","2"
"Template","2026-01-01","0","5","","99","0","0","0","0","","1","0","0","0","3"
"AXA","2026-02-01","0","31","yearly","1800","0","0","0","0","2027-02-01","0","0","12","0","4"
"Careem trip","2026-02-03","0","50","","40","0","0","0","0","","0","0","0","1","5"
"Oil change","2026-02-10","2500","1","","250","0","0","0","12500","","0","10000","6","0","6"
""".trimIndent()

    val ELECTRIC = """
"## Vehicle"
"Name","DistUnit","FuelUnit","ImportCSVDateFormat","TankCount","Tank1Type","Tank1Capacity"
"Model 3","0","0","yyyy-MM-dd","1","600","75"
"## Log"
"Data","Odo (km)","Fuel (litres)","Full","Price (optional)","TankNumber","FuelType","VolumePrice"
"2026-03-01 20:00","5000","40.5","1","","1","601","0.29"
"2026-03-05 12:00","5300","30","0","36","1","602",""
""".trimIndent()
}
