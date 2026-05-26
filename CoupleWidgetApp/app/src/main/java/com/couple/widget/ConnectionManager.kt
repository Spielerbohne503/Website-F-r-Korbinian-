package com.couple.widget

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class ConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionManager"
        private const val SERVICE_TYPE = "_couplewidget._tcp."
        private const val SERVICE_NAME_PREFIX = "CoupleWidget"
        private const val PORT = 8765
        private const val PREFS_P2P = "p2p_device"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var writer: PrintWriter? = null

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS_P2P, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    private val myServiceName get() = "$SERVICE_NAME_PREFIX-$deviceId"

    private var registrationDone = false
    private var discoveryRunning = false

    @Volatile
    var isConnected = false
        private set

    @Volatile
    private var resolving = false

    var onMessageReceived: ((MessageData) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    fun start() {
        startServer()
        registerNsdService()
        discoverNsdServices()
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Listening on port $PORT")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Incoming: ${socket.inetAddress}")
                    acceptSocket(socket)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Server stopped: ${e.message}")
            }
        }
    }

    private fun acceptSocket(socket: Socket) {
        activeSocket?.runCatching { close() }
        activeSocket = socket
        writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
        setConnected(true)
        listenOnSocket(socket)
    }

    private fun listenOnSocket(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    MessageData.fromJson(line)?.let { onMessageReceived?.invoke(it) }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Read stopped: ${e.message}")
            } finally {
                setConnected(false)
                // Retry discovery after disconnection
                delay(3000)
                if (!isConnected) discoverNsdServices()
            }
        }
    }

    fun send(message: MessageData) {
        scope.launch {
            try {
                writer?.println(message.toJson())
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
                setConnected(false)
            }
        }
    }

    private fun setConnected(value: Boolean) {
        if (isConnected != value) {
            isConnected = value
            onConnectionChanged?.invoke(value)
        }
    }

    // --- NSD Registration ---

    private fun registerNsdService() {
        val info = NsdServiceInfo().apply {
            serviceName = myServiceName
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Register error: ${e.message}")
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registrationDone = true
            Log.d(TAG, "NSD registered: ${info.serviceName}")
        }
        override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
            Log.e(TAG, "NSD register failed: $code")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) {
            registrationDone = false
        }
        override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
    }

    // --- NSD Discovery ---

    private fun discoverNsdServices() {
        if (discoveryRunning) return
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            discoveryRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Discovery start error: ${e.message}")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {
            Log.d(TAG, "Discovery started")
        }
        override fun onDiscoveryStopped(type: String) {
            discoveryRunning = false
        }
        override fun onStartDiscoveryFailed(type: String, code: Int) {
            discoveryRunning = false
            Log.e(TAG, "Discovery start failed: $code")
        }
        override fun onStopDiscoveryFailed(type: String, code: Int) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            // Skip our own service
            if (service.serviceName == myServiceName) return
            // Only handle our app's services
            if (!service.serviceName.startsWith(SERVICE_NAME_PREFIX)) return
            Log.d(TAG, "Partner found: ${service.serviceName}")
            if (!isConnected && !resolving) {
                resolving = true
                try {
                    nsdManager.resolveService(service, createResolveListener())
                } catch (e: Exception) {
                    resolving = false
                    Log.e(TAG, "Resolve error: ${e.message}")
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "Partner lost: ${service.serviceName}")
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
            resolving = false
            Log.e(TAG, "Resolve failed: $code")
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
            resolving = false
            val host = info.host?.hostAddress ?: return
            Log.d(TAG, "Resolved: $host:${info.port}")
            if (!isConnected) connectTo(host, info.port)
        }
    }

    private fun connectTo(host: String, port: Int) {
        scope.launch {
            // Small delay so both devices don't connect simultaneously
            delay((200L..800L).random())
            if (isConnected) return@launch
            try {
                val socket = Socket(host, port)
                Log.d(TAG, "Connected to partner at $host")
                acceptSocket(socket)
            } catch (e: Exception) {
                Log.d(TAG, "Connect failed: ${e.message}")
            }
        }
    }

    fun stop() {
        scope.cancel()
        if (registrationDone) {
            runCatching { nsdManager.unregisterService(registrationListener) }
        }
        if (discoveryRunning) {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
        activeSocket?.runCatching { close() }
        serverSocket?.runCatching { close() }
    }
}
