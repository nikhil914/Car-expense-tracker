package ae.sayyara.automotive

import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager

/**
 * Guarded reads of global-area vehicle properties. Carmakers may not implement a property in their VHAL, or may
 * block it for third-party apps. So every access first checks [isAvailable], which needs a config, and treats any
 * exception or non-AVAILABLE status as "no value".
 */
internal object AaosCarReader {
    /** VEHICLE_AREA_TYPE_GLOBAL properties use area id 0. */
    const val AREA_GLOBAL = 0

    fun config(pm: CarPropertyManager, propertyId: Int): CarPropertyConfig<*>? = try {
        pm.getCarPropertyConfig(propertyId)
    } catch (e: Exception) {
        null // SecurityException (no permission) or IllegalArgumentException on some releases
    }

    fun isAvailable(pm: CarPropertyManager, propertyId: Int): Boolean {
        if (config(pm, propertyId) == null) return false
        return try {
            pm.isPropertyAvailable(propertyId, AREA_GLOBAL)
        } catch (e: Exception) {
            false
        }
    }

    fun readFloat(pm: CarPropertyManager, propertyId: Int): Float? = read<Float>(pm, propertyId)
    fun readInt(pm: CarPropertyManager, propertyId: Int): Int? = read<Int>(pm, propertyId)
    fun readBoolean(pm: CarPropertyManager, propertyId: Int): Boolean? = read<Boolean>(pm, propertyId)

    private inline fun <reified T : Any> read(pm: CarPropertyManager, propertyId: Int): T? {
        if (config(pm, propertyId) == null) return null
        return try {
            val v: CarPropertyValue<T>? = pm.getProperty(propertyId, AREA_GLOBAL)
            if (v != null && v.status == CarPropertyValue.STATUS_AVAILABLE) v.value as? T else null
        } catch (e: Exception) {
            // PropertyNotAvailableException, PropertyAccessDeniedSecurityException, IllegalArgumentException, ...
            null
        }
    }

    /** Human-readable name for logs. */
    fun name(propertyId: Int): String = try {
        VehiclePropertyIds.toString(propertyId)
    } catch (e: Exception) {
        "0x" + propertyId.toString(16)
    }
}
