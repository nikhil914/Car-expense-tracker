package ae.sayyara.app.data

import ae.sayyara.app.data.db.FuelPriceDao
import ae.sayyara.app.data.db.FuelPriceEntity
import ae.sayyara.app.data.db.toEntity
import ae.sayyara.app.data.db.toModel
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.PriceStatus
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.uae.UaeFuelPrices
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Remote configuration for the published UAE price feed. */
object PriceFeedConfig {
    /**
     * JSON feed in the format of [UaeFuelPrices.toJson]. Placeholder host: replace with the real
     * Sayyara reference-data endpoint (UAE region) before release.
     */
    const val PRICE_FEED_URL = "https://prices.sayyara.example/v1/uae/fuel-prices.json"

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000
    const val MAX_BYTES = 1_000_000
}

/** Result of one feed pull. [newMonth] is set when the feed brought a month later than any we had. */
data class PriceRefreshOutcome(val received: Int, val written: Int, val newMonth: YearMonth?)

class RoomFuelPriceRepository(
    private val dao: FuelPriceDao,
    private val feedUrl: String = PriceFeedConfig.PRICE_FEED_URL,
    private val clock: NowProvider = SystemNow,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FuelPriceRepository {

    private val seedLock = Mutex()
    @Volatile private var seeded = false

    /**
     * Inserts the bundled seed with INSERT OR IGNORE. Runs once per process: on first run it fills the
     * table, and after an app update it adds any months the new seed knows about, without overwriting
     * prices that a refresh already stored.
     */
    suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            val now = clock.now().toEpochMilliseconds()
            dao.insertIgnore(UaeFuelPrices.seed.map { it.toEntity(now) })
            seeded = true
        }
    }

    override fun observeAll(): Flow<List<FuelPrice>> =
        dao.observeActive().onStart { ensureSeeded() }.map { list -> list.map { it.toModel() } }

    override suspend fun priceFor(month: YearMonth, grade: FuelGrade): FuelPrice? {
        ensureSeeded()
        return dao.get(month.toString(), grade.name)?.toModel()
    }

    override suspend fun latest(grade: FuelGrade): FuelPrice? {
        ensureSeeded()
        return dao.latest(grade.name)?.toModel()
    }

    suspend fun latestMonth(): YearMonth? {
        ensureSeeded()
        return dao.maxMonth()?.let(YearMonth::parse)
    }

    /** Contract version: never throws; network/parse problems report "no new month". */
    override suspend fun refresh(): Boolean =
        try {
            refreshDetailed().newMonth != null
        } catch (e: IOException) {
            false
        } catch (e: IllegalArgumentException) { // malformed JSON from the feed
            false
        }

    /** Throws [IOException] on network failure so workers can retry. */
    suspend fun refreshDetailed(): PriceRefreshOutcome = withContext(io) {
        ensureSeeded()
        val before = dao.maxMonth()?.let(YearMonth::parse)
        val remote = UaeFuelPrices.fromJson(fetch(feedUrl))
        val now = clock.now().toEpochMilliseconds()
        val toWrite = remote.filter { p -> shouldReplace(dao.get(p.month.toString(), p.grade.name), p) }
        if (toWrite.isNotEmpty()) dao.upsertAll(toWrite.map { it.toEntity(now) })
        val after = dao.maxMonth()?.let(YearMonth::parse)
        PriceRefreshOutcome(
            received = remote.size,
            written = toWrite.size,
            newMonth = after?.takeIf { before == null || it > before },
        )
    }

    /** A provisional remote price never downgrades a verified local one. */
    private fun shouldReplace(local: FuelPriceEntity?, remote: FuelPrice): Boolean = when {
        local == null -> true
        local.status == PriceStatus.VERIFIED && remote.status == PriceStatus.PROVISIONAL -> false
        else -> local.aedPerLitre != remote.aedPerLitre || local.status != remote.status ||
            local.sourceUrl != remote.sourceUrl
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = PriceFeedConfig.CONNECT_TIMEOUT_MS
            conn.readTimeout = PriceFeedConfig.READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            conn.useCaches = false
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Price feed HTTP $code")
            return conn.inputStream.use { input ->
                val bytes = input.readNBytesCompat(PriceFeedConfig.MAX_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** Reads at most [limit] bytes; throws if the stream is larger (InputStream.readNBytes is API 33+). */
internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw IOException("Response larger than $limit bytes")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
