package com.tomeofhealing.app.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.InputDevice

/** Runtime capability snapshot used to tune stylus behavior without hard dependency on OEM SDKs. */
data class StylusCapabilities(
    val hasTouchscreen: Boolean,
    val hasSamsungHardware: Boolean,
    val detectedStylusDevice: Boolean,
    val manufacturer: String,
    val model: String
) {
    val enhancedStylusMode: Boolean get() = detectedStylusDevice || hasSamsungHardware

    companion object {
        fun detect(context: Context): StylusCapabilities {
            val pm = context.packageManager
            val stylusDetected = InputDevice.getDeviceIds().any { id ->
                InputDevice.getDevice(id)?.sources?.let { sources ->
                    sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
                } == true
            }
            return StylusCapabilities(
                hasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
                hasSamsungHardware = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
                detectedStylusDevice = stylusDetected,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty()
            )
        }
    }
}
