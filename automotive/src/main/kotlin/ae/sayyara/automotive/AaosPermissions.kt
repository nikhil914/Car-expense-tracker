package ae.sayyara.automotive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Car permissions (android.car.Car.PERMISSION_*), spelled out so this file also reads clearly without the car stubs.
 *
 * Dangerous (runtime prompt): CAR_ENERGY (FUEL_LEVEL, EV_BATTERY_LEVEL, RANGE_REMAINING), CAR_SPEED,
 * CAR_MILEAGE_3P (PERF_ODOMETER for third-party apps; the older CAR_MILEAGE is signature|privileged).
 * Normal (granted at install): CAR_ENERGY_PORTS (FUEL_DOOR_OPEN, EV_CHARGE_PORT_*), CAR_INFO (INFO_FUEL_CAPACITY,
 * INFO_EV_BATTERY_CAPACITY), CAR_POWERTRAIN (IGNITION_STATE, PARKING_BRAKE_ON).
 */
object AaosPermissions {
    const val CAR_ENERGY = "android.car.permission.CAR_ENERGY"
    const val CAR_ENERGY_PORTS = "android.car.permission.CAR_ENERGY_PORTS"
    const val CAR_INFO = "android.car.permission.CAR_INFO"
    const val CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"
    const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    const val CAR_MILEAGE_3P = "android.car.permission.CAR_MILEAGE_3P"

    /** Asked for in one runtime prompt. Only CAR_ENERGY is required for detection; the rest improve it. */
    fun runtime(): List<String> = buildList {
        add(CAR_ENERGY)
        add(CAR_SPEED)
        add(CAR_MILEAGE_3P) // newer platforms only; an unknown permission is simply reported as rejected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun missingRuntime(context: Context): List<String> = runtime().filterNot { granted(context, it) }

    fun canDetect(context: Context): Boolean = granted(context, CAR_ENERGY)
}
