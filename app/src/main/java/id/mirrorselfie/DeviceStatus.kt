package id.mirrorselfie

import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

object DeviceStatus {
    fun battery(context: Context) = context.getSystemService(BatteryManager::class.java)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: -1
    @Suppress("DEPRECATION")
    fun freeBytes(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes }.getOrDefault(-1)
}
