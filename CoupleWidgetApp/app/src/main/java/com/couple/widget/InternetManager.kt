package com.couple.widget

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class InternetManager(private val context: Context) {

    companion object {
        private const val TAG = "InternetManager"
        private val TOPIC = "cw/${CoupleConfig.COUPLE_ID}"
        private const val QOS = 1
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mqttClient: MqttClient? = null

    @Volatile
    var isConnected = false
        private set

    var onMessageReceived: ((MessageData) -> Unit)? = null

    private val deviceId: String by lazy {
        context.getSharedPreferences("p2p_device", Context.MODE_PRIVATE)
            .getString("device_id", "??") ?: "??"
    }

    fun start() {
        scope.launch { maintainConnection() }
    }

    private suspend fun maintainConnection() {
        while (scope.isActive) {
            try {
                connectAndListen()
            } catch (e: Exception) {
                Log.d(TAG, "MQTT disconnected: ${e.message}")
            }
            isConnected = false
            delay(15_000)
        }
    }

    private suspend fun connectAndListen() {
        val clientId = "cw_${deviceId}_${System.currentTimeMillis() % 100_000}"
        val client = MqttClient(CoupleConfig.MQTT_BROKER, clientId, MemoryPersistence())
        mqttClient = client

        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                Log.d(TAG, "Connection lost: ${cause?.message}")
            }
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            override fun messageArrived(topic: String, msg: MqttMessage) {
                handleIncoming(msg.payload)
            }
        })

        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            keepAliveInterval = 60
            connectionTimeout = 20
        }

        withContext(Dispatchers.IO) {
            client.connect(opts)
            client.subscribe(TOPIC, QOS)
        }
        isConnected = true
        Log.d(TAG, "MQTT connected, topic=$TOPIC")

        while (scope.isActive && client.isConnected) {
            delay(5_000)
        }
        client.runCatching { disconnect() }
    }

    private fun handleIncoming(payload: ByteArray) {
        try {
            val decrypted = CryptoHelper.decrypt(String(payload, Charsets.UTF_8))
            val obj = JSONObject(decrypted)
            if (obj.getString("from") == deviceId) return
            onMessageReceived?.invoke(
                MessageData(obj.getString("type"), obj.getString("content"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    fun send(message: MessageData) {
        scope.launch {
            try {
                val client = mqttClient?.takeIf { it.isConnected } ?: return@launch
                val json = JSONObject().apply {
                    put("from", deviceId)
                    put("type", message.type)
                    put("content", message.content)
                }.toString()
                val encrypted = CryptoHelper.encrypt(json)
                client.publish(TOPIC, encrypted.toByteArray(Charsets.UTF_8), QOS, false)
                Log.d(TAG, "MQTT sent: ${message.type}")
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    fun stop() {
        scope.cancel()
        mqttClient?.runCatching { disconnect() }
    }
}
