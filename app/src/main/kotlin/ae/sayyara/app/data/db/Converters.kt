package ae.sayyara.app.data.db

import ae.sayyara.core.model.LatLon
import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Room type converters. Enums use Room's built-in name mapping; timestamps are stored as Long. */
class Converters {
    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun polylineToString(value: List<LatLon>?): String? =
        value?.let { json.encodeToString(polylineSerializer, it) }

    @TypeConverter
    fun stringToPolyline(value: String?): List<LatLon>? =
        value?.let { if (it.isBlank()) emptyList() else json.decodeFromString(polylineSerializer, it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val polylineSerializer = ListSerializer(LatLon.serializer())
    }
}
