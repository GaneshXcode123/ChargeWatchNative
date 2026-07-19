package com.chargewatch.app

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chargewatch.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var monitoring = false

    // Local receiver just for live UI while the app is open in the foreground.
    // The actual alerting is handled independently by BatteryMonitorService.
    private val uiBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = (level * 100) / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            binding.pctReadout.text = "$pct%"
            binding.stateReadout.text = if (charging) "Charging" else "Not charging"
        }
    }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        monitoring = Prefs.isMonitoring(this)
        setupTargetSlider()
        setupModeButtons()
        setupToggleButton()
        setupBatteryOptButton()
        updateToggleUi()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            uiBatteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uiBatteryReceiver)
    }

    @SuppressLint("SetTextI18n")
    private fun setupTargetSlider() {
        val target = Prefs.getTarget(this)
        binding.targetSlider.progress = (target - 10).coerceIn(0, 90)
        binding.targetNum.text = "$target%"

        binding.targetSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 10
                binding.targetNum.text = "$value%"
                Prefs.setTarget(this@MainActivity, value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupModeButtons() {
        when (Prefs.getMode(this)) {
            "message" -> binding.modeGroup.check(binding.modeMessage.id)
            "alarm" -> binding.modeGroup.check(binding.modeAlarm.id)
            else -> binding.modeGroup.check(binding.modeBoth.id)
        }
        binding.modeGroup.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            val mode = when (checkedId) {
                binding.modeMessage.id -> "message"
                binding.modeAlarm.id -> "alarm"
                else -> "both"
            }
            Prefs.setMode(this, mode)
        }
    }

    private fun setupToggleButton() {
        binding.toggleBtn.setOnClickListener {
            if (!monitoring) {
                val intent = Intent(this, BatteryMonitorService::class.java).setAction(BatteryMonitorService.ACTION_START)
                ContextCompat.startForegroundService(this, intent)
                monitoring = true
            } else {
                val intent = Intent(this, BatteryMonitorService::class.java).setAction(BatteryMonitorService.ACTION_STOP)
                startService(intent)
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(2)
                monitoring = false
            }
            updateToggleUi()
        }
    }

    private fun updateToggleUi() {
        if (monitoring) {
            binding.toggleBtn.text = "Stop monitoring"
            binding.toggleBtn.setBackgroundResource(R.drawable.bg_button_stop)
            binding.statusLine.text = "Monitoring in background — safe to close the app."
        } else {
            binding.toggleBtn.text = "Start monitoring"
            binding.toggleBtn.setBackgroundResource(R.drawable.bg_button_primary)
            binding.statusLine.text = "Set a target and tap Start monitoring."
        }
    }

    private fun setupBatteryOptButton() {
        binding.batteryOptBtn.setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                binding.statusLine.text = "Background running is already allowed."
            }
        }
    }
}
