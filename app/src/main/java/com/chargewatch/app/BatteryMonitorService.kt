package com.chargewatch.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class BatteryMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.chargewatch.app.action.START"
        const val ACTION_STOP = "com.chargewatch.app.action.STOP"
        const val ACTION_STOP_ALARM = "com.chargewatch.app.action.STOP_ALARM"

        private const val MONITOR_CHANNEL = "monitor_channel"
        private const val ALERT_CHANNEL = "alert_channel"
        private const val MONITOR_NOTIF_ID = 1
        private const val ALERT_NOTIF_ID = 2
    }

    private var alerted = false
    private var receiverRegistered = false
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = (level * 100) / scale

            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            updateMonitorNotification(pct, charging)

            val target = Prefs.getTarget(applicationContext)
            if (pct >= target) {
                if (!alerted) {
                    alerted = true
                    fireAlert(pct)
                }
            } else {
                alerted = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarmSoundOnly()
                return START_STICKY
            }
            ACTION_STOP -> {
                Prefs.setMonitoring(applicationContext, false)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Prefs.setMonitoring(applicationContext, true)
                startForeground(MONITOR_NOTIF_ID, buildMonitorNotification(null, null))
                if (!receiverRegistered) {
                    androidx.core.content.ContextCompat.registerReceiver(
                        this,
                        batteryReceiver,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    receiverRegistered = true
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        stopAlarmSoundOnly()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Notifications ----

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_channel_desc)
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alert_channel_desc)
            enableVibration(true)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttrs)
        }

        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(alertChannel)
    }

    private fun buildMonitorNotification(pct: Int?, charging: Boolean?): android.app.Notification {
        val target = Prefs.getTarget(applicationContext)
        val text = if (pct != null) {
            val state = if (charging == true) "Charging" else "Not charging"
            "$pct% • $state • target $target%"
        } else {
            "Waiting for battery data… target $target%"
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPI = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BatteryMonitorService::class.java).setAction(ACTION_STOP)
        val stopPI = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Charge Watch is monitoring")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentPI)
            .addAction(R.drawable.ic_stat_bolt, "Stop monitoring", stopPI)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateMonitorNotification(pct: Int, charging: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(MONITOR_NOTIF_ID, buildMonitorNotification(pct, charging))
    }

    private fun fireAlert(pct: Int) {
        val mode = Prefs.getMode(applicationContext)
        val target = Prefs.getTarget(applicationContext)
        val message = if (pct >= 100) "Your phone is fully charged." else "Charge reached your $target% target."

        if (mode == "message" || mode == "both") {
            sendAlertNotification(message)
        }
        if (mode == "alarm" || mode == "both") {
            startAlarmSound()
            vibrate()
        }
    }

    private fun sendAlertNotification(message: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPI = PendingIntent.getActivity(
            this, 1, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAlarmIntent = Intent(this, BatteryMonitorService::class.java).setAction(ACTION_STOP_ALARM)
        val stopAlarmPI = PendingIntent.getService(
            this, 1, stopAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Charge Watch")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .addAction(R.drawable.ic_stat_bolt, "Stop alarm", stopAlarmPI)
            .setFullScreenIntent(contentPI, true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_NOTIF_ID, notification)
    }

    private fun startAlarmSound() {
        if (mediaPlayer != null) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "ChargeWatch::AlarmWakeLock"
            ).apply { acquire(10 * 60 * 1000L) } // safety timeout: 10 minutes

            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@BatteryMonitorService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // If playback fails for any reason, the notification + vibration still alert the user.
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopAlarmSoundOnly() {
        mediaPlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        mediaPlayer = null

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALERT_NOTIF_ID)
    }
}
