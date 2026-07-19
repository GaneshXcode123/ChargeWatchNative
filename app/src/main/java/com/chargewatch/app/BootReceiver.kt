package com.chargewatch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isMonitoring(context)) {
                val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                    .setAction(BatteryMonitorService.ACTION_START)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
