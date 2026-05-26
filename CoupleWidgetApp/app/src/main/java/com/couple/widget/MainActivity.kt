package com.couple.widget

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.couple.widget.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connected = intent.getBooleanExtra(P2PService.EXTRA_IS_CONNECTED, false)
            binding.tvStatus.text = if (connected) "Partner verbunden" else "Suche Partner..."
            binding.statusDot.setBackgroundResource(
                if (connected) R.drawable.dot_online else R.drawable.dot_offline
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        startForegroundService(Intent(this, P2PService::class.java))

        binding.btnCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(P2PService.PREFS, Context.MODE_PRIVATE)
        val connected = prefs.getBoolean(P2PService.KEY_IS_CONNECTED, false)
        binding.tvStatus.text = if (connected) "Partner verbunden" else "Suche Partner..."
        binding.statusDot.setBackgroundResource(
            if (connected) R.drawable.dot_online else R.drawable.dot_offline
        )

        ContextCompat.registerReceiver(
            this,
            connectionReceiver,
            IntentFilter(P2PService.ACTION_CONNECTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(connectionReceiver) }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }
}
