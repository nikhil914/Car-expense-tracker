package ae.sayyara.app.car

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.LatLon
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class StationBrand(val label: String) { ADNOC("ADNOC"), ENOC("ENOC"), EMARAT("Emarat") }

/**
 * A fuel station from the small bundled list.
 *
 * [approximate] is always true for the bundled list: the coordinates are hand-placed from public maps
 * to roughly the right block (expect an error of a few hundred metres). They are good enough to sort
 * "nearby" and start navigation, but they are not surveyed. The UI must label them as approximate.
 */
data class FuelStation(
    val id: String,
    val brand: StationBrand,
    val area: String,
    val emirate: Emirate,
    val location: LatLon,
    val approximate: Boolean = true,
) {
    val name: String get() = "${brand.label} ${area}"
}

/**
 * Small offline list of major ADNOC / ENOC / Emarat stations in Dubai and Abu Dhabi.
 *
 * TODO(phase 2): replace with a station feed (or Places search) and move the list to core so the
 * automotive module (which keeps its own copy in ae.sayyara.automotive.AaosStations) can share it.
 */
object BundledStations {
    val all: List<FuelStation> = listOf(
        // Dubai (approximate coordinates)
        FuelStation("dxb-enoc-barsha", StationBrand.ENOC, "Sheikh Zayed Rd, Al Barsha", Emirate.DUBAI, LatLon(25.1180, 55.2010)),
        FuelStation("dxb-enoc-wasl", StationBrand.ENOC, "Al Wasl Rd, Jumeirah", Emirate.DUBAI, LatLon(25.1890, 55.2420)),
        FuelStation("dxb-enoc-mirdif", StationBrand.ENOC, "Algeria St, Mirdif", Emirate.DUBAI, LatLon(25.2200, 55.4190)),
        FuelStation("dxb-adnoc-alkhail", StationBrand.ADNOC, "Al Khail Rd, Al Quoz", Emirate.DUBAI, LatLon(25.1530, 55.2400)),
        FuelStation("dxb-adnoc-marina", StationBrand.ADNOC, "Al Sufouh, near Dubai Marina", Emirate.DUBAI, LatLon(25.0900, 55.1520)),
        FuelStation("dxb-adnoc-dso", StationBrand.ADNOC, "Dubai Silicon Oasis", Emirate.DUBAI, LatLon(25.1210, 55.3800)),
        FuelStation("dxb-emarat-qusais", StationBrand.EMARAT, "Damascus St, Al Qusais", Emirate.DUBAI, LatLon(25.2780, 55.3770)),
        FuelStation("dxb-emarat-garhoud", StationBrand.EMARAT, "Al Garhoud", Emirate.DUBAI, LatLon(25.2420, 55.3510)),
        FuelStation("dxb-emarat-jebelali", StationBrand.EMARAT, "Jebel Ali", Emirate.DUBAI, LatLon(25.0050, 55.1050)),
        // Abu Dhabi (approximate coordinates)
        FuelStation("auh-adnoc-khalidiyah", StationBrand.ADNOC, "Corniche, Al Khalidiyah", Emirate.ABU_DHABI, LatLon(24.4700, 54.3460)),
        FuelStation("auh-adnoc-khaleej", StationBrand.ADNOC, "Al Khaleej Al Arabi St", Emirate.ABU_DHABI, LatLon(24.4420, 54.3900)),
        FuelStation("auh-adnoc-yas", StationBrand.ADNOC, "Yas Island", Emirate.ABU_DHABI, LatLon(24.4880, 54.6080)),
        FuelStation("auh-adnoc-khalifa", StationBrand.ADNOC, "Khalifa City A", Emirate.ABU_DHABI, LatLon(24.4200, 54.5720)),
        FuelStation("auh-adnoc-mussafah", StationBrand.ADNOC, "Mussafah", Emirate.ABU_DHABI, LatLon(24.3600, 54.5000)),
        FuelStation("auh-enoc-muroor", StationBrand.ENOC, "Muroor Rd", Emirate.ABU_DHABI, LatLon(24.4600, 54.3900)),
    )

    /** Stations sorted by distance from [from] (km), or the full list in bundled order when no location is known. */
    fun nearest(from: LatLon?, limit: Int): List<Pair<FuelStation, Double?>> =
        if (from == null) all.take(limit).map { it to null }
        else all.map { it to distanceKm(from, it.location) }.sortedBy { it.second }.take(limit)

    /** Great-circle distance in km. */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
