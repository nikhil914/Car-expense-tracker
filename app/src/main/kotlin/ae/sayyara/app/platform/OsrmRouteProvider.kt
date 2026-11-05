package ae.sayyara.app.platform

import ae.sayyara.core.model.LatLon
import ae.sayyara.core.route.RouteProvider
import ae.sayyara.core.route.RouteResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [RouteProvider] backed by the OSRM HTTP API (`/route/v1/driving`, `overview=full`, `geometries=geojson`).
 *
 * NOTE: router.project-osrm.org is the public DEMO server: no SLA, heavy rate limits, and its usage
 * policy forbids production traffic. Production needs a self-hosted OSRM or Valhalla instance built
 * from a UAE / GCC OpenStreetMap extract (e.g. Geofabrik GCC states), ideally hosted in a UAE region.
 */
class OsrmRouteProvider(
    private val baseUrl: String = DEMO_BASE_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RouteProvider {

    override suspend fun route(from: LatLon, to: LatLon): RouteResult? = withContext(io) {
        try {
            parse(get(buildUrl(from, to)))
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) { // bad JSON shape
            null
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    internal fun buildUrl(from: LatLon, to: LatLon): String {
        // OSRM coordinate order is lon,lat.
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        return "$baseUrl/route/v1/driving/$coords?overview=full&geometries=geojson&alternatives=false&steps=false"
    }

    internal fun parse(body: String): RouteResult? {
        val root = json.parseToJsonElement(body).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "Ok") return null
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val distanceM = route["distance"]?.jsonPrimitive?.double ?: return null
        val durationS = route["duration"]?.jsonPrimitive?.double ?: return null
        val coords = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray.orEmpty()
        val polyline = coords.map { point ->
            val p = point.jsonArray
            LatLon(lat = p[1].jsonPrimitive.double, lon = p[0].jsonPrimitive.double)
        }
        return RouteResult(distanceKm = distanceM / 1000.0, durationMin = durationS / 60.0, polyline = polyline)
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            // The OSRM demo usage policy requires an identifying User-Agent.
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("OSRM HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEMO_BASE_URL = "https://router.project-osrm.org"
        private const val USER_AGENT = "Sayyara/0.1 (Android; UAE fuel tracker)"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
