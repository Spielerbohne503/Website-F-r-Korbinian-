package com.couple.widget

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class P2PService : Service() {

    companion object {
        private const val TAG = "P2PService"
        const val CHANNEL_ID = "couple_p2p"
        private const val NOTIF_ID = 1

        const val PREFS = "couple_data"
        const val KEY_LAST_TEXT = "last_text"
        const val KEY_HAS_IMAGE = "has_image"
        const val KEY_IS_CONNECTED = "is_connected"
        const val IMAGE_FILENAME = "last_received.jpg"

        const val ACTION_SEND_TEXT = "com.couple.widget.SEND_TEXT"
        const val ACTION_SEND_IMAGE = "com.couple.widget.SEND_IMAGE"
        const val EXTRA_TEXT = "text"
        const val EXTRA_IMAGE_B64 = "image_b64"
        const val ACTION_CONNECTION_STATE = "com.couple.widget.CONNECTION_STATE"
        const val EXTRA_IS_CONNECTED = "is_connected"

        fun sendText(context: Context, text: String) {
            context.startForegroundService(Intent(context, P2PService::class.java).apply {
                action = ACTION_SEND_TEXT
                putExtra(EXTRA_TEXT, text)
            })
        }

        fun sendImage(context: Context, base64: String) {
            context.startForegroundService(Intent(context, P2PService::class.java).apply {
                action = ACTION_SEND_IMAGE
                putExtra(EXTRA_IMAGE_B64, base64)
            })
        }
    }

    private lateinit var connectionManager: ConnectionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Suche Partner..."))

        connectionManager = ConnectionManager(this).apply {
            onConnectionChanged = { connected ->
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_IS_CONNECTED, connected).apply()
                updateNotification(if (connected) "Partner verbunden" else "Partner getrennt")
                CoupleWidget.updateAll(this@P2PService)
                sendBroadcast(Intent(ACTION_CONNECTION_STATE).apply {
                    putExtra(EXTRA_IS_CONNECTED, connected)
                    setPackage(packageName)
                })
            }
            onMessageReceived = { message -> handleIncoming(message) }
        }
        connectionManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_STICKY
                connectionManager.send(MessageData("text", text))
            }
            ACTION_SEND_IMAGE -> {
                val b64 = intent.getStringExtra(EXTRA_IMAGE_B64) ?: return START_STICKY
                connectionManager.send(MessageData("image", b64))
            }
        }
        return START_STICKY
    }

    private fun handleIncoming(message: MessageData) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        when (message.type) {
            "text" -> {
                prefs.putString(KEY_LAST_TEXT, message.content)
                    .putBoolean(KEY_HAS_IMAGE, false)
                    .apply()
                CoupleWidget.updateAll(this)
            }
            "image" -> {
                saveImage(message.content)
                prefs.putBoolean(KEY_HAS_IMAGE, true)
                    .putString(KEY_LAST_TEXT, "")
                    .apply()
                CoupleWidget.updateAll(this)
            }
        }
        Log.d(TAG, "Received: ${message.type}")
    }

    private fun saveImage(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            FileOutputStream(File(filesDir, IMAGE_FILENAME)).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.e(TAG, "Save image failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Couple Widget", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "P2P Verbindung" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectionManager.stop()
        super.onDestroy()
    }
}
