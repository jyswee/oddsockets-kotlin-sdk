package com.oddsockets

import com.oddsockets.config.OddSocketsConfig
import com.oddsockets.exception.*
import com.oddsockets.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Main client for connecting to OddSockets.
 *
 * This class provides the primary interface for real-time messaging with OddSockets.
 * It handles connection management, worker assignment, and provides access to channels.
 *
 * @property config The client configuration
 */
class OddSocketsClient(
    private val config: OddSocketsConfig
) {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // HTTP client for REST operations
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(WebSockets)
    }
    
    // Connection state management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _workerInfo = MutableStateFlow<Pair<String?, String?>>(null to null)
    val workerInfo: StateFlow<Pair<String?, String?>> = _workerInfo.asStateFlow()
    
    // WebSocket connection
    private val webSocketSession = AtomicReference<WebSocketSession?>(null)
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0
    
    // Event handling
    private val eventHandlers = ConcurrentHashMap<EventType, MutableList<SuspendEventHandler>>()
    private val _eventFlow = MutableSharedFlow<Pair<EventType, Any?>>()
    val eventFlow: SharedFlow<Pair<EventType, Any?>> = _eventFlow.asSharedFlow()
    
    // Message handling
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow: SharedFlow<Message> = _messageFlow.asSharedFlow()
    
    // Channel management
    private val channels = ConcurrentHashMap<String, OddSocketsChannel>()
    
    // User ID and client identifier for session stickiness
    val userId: String = config.userId ?: generateClientIdentifier()
    val clientIdentifier: String = generateClientIdentifier()
    
    // Session information
    private var sessionInfo: Map<String, Any?>? = null
    
    /**
     * Whether the client is currently connected.
     */
    val isConnected: Boolean
        get() = connectionState.value.isConnected
    
    /**
     * Whether the client is currently connecting.
     */
    val isConnecting: Boolean
        get() = connectionState.value.isConnecting
    
    /**
     * Whether the client is disconnected.
     */
    val isDisconnected: Boolean
        get() = connectionState.value.isDisconnected
    
    init {
        config.validate()
        logger.info { "OddSockets client initialized for user: $userId" }
        
        if (config.autoConnect) {
            scope.launch {
                connect()
            }
        }
    }
    
    /**
     * Connects to OddSockets.
     * @throws OddSocketsException if connection fails
     */
    suspend fun connect() {
        if (isConnected || isConnecting) {
            logger.debug { "Already connected or connecting" }
            return
        }
        
        logger.info { "Connecting to OddSockets..." }
        updateConnectionState(ConnectionState.CONNECTING)
        
        try {
            // Get worker assignment from manager
            val assignment = getWorkerAssignment()
            val workerUrl = assignment.url ?: throw ConnectionException.workerAssignmentFailed("No worker URL provided")
            
            _workerInfo.value = assignment.workerId to workerUrl
            
            // Connect to assigned worker
            connectToWorker(workerUrl)
            
            updateConnectionState(ConnectionState.CONNECTED)
            reconnectAttempts = 0
            
            // Start heartbeat
            startHeartbeat()
            
            emitEvent(EventType.CONNECTED, mapOf("worker_id" to assignment.workerId, "worker_url" to workerUrl))
            logger.info { "Connected to OddSockets worker: ${assignment.workerId}" }
            
        } catch (e: Exception) {
            updateConnectionState(ConnectionState.FAILED)
            val exception = OddSocketsException.from(e)
            emitEvent(EventType.ERROR, exception)
            throw exception
        }
    }
    
    /**
     * Disconnects from OddSockets.
     */
    suspend fun disconnect() {
        logger.info { "Disconnecting from OddSockets..." }
        
        // Cancel reconnection attempts
        reconnectJob?.cancel()
        reconnectJob = null
        
        // Stop heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Close WebSocket connection
        webSocketSession.get()?.close()
        webSocketSession.set(null)
        
        // Update state
        updateConnectionState(ConnectionState.DISCONNECTED)
        _workerInfo.value = null to null
        
        // Notify channels
        channels.values.forEach { it.handleDisconnection() }
        
        emitEvent(EventType.DISCONNECTED, null)
        logger.info { "Disconnected from OddSockets" }
    }
    
    /**
     * Gets a channel instance.
     * @param channelName The channel name
     * @return The channel instance
     * @throws ChannelException if channel name is invalid
     */
    fun channel(channelName: String): OddSocketsChannel {
        validateChannelName(channelName)
        
        return channels.computeIfAbsent(channelName) { name ->
            OddSocketsChannel(name, this, scope)
        }
    }
    
    /**
     * Publishes multiple messages in bulk.
     * @param messages The messages to publish
     * @return The bulk results
     * @throws OddSocketsException if bulk publish fails
     */
    suspend fun publishBulk(messages: List<BulkMessage>): List<BulkResult> {
        if (!isConnected) {
            throw ConnectionException("Not connected to OddSockets")
        }
        
        if (messages.isEmpty()) {
            return emptyList()
        }
        
        logger.debug { "Publishing ${messages.size} messages in bulk" }
        
        return try {
            val request = mapOf(
                "type" to "bulk_publish",
                "messages" to messages.map { bulkMessage ->
                    mapOf(
                        "channel" to bulkMessage.channel,
                        "message" to bulkMessage.message,
                        "options" to bulkMessage.options
                    )
                }
            )
            
            val response = sendRequest(request)
            val results = response["results"] as? List<*> ?: throw MessageException("Invalid bulk publish response")
            
            results.map { result ->
                val resultMap = result as? Map<*, *> ?: throw MessageException("Invalid bulk result format")
                val success = resultMap["success"] as? Boolean ?: false
                
                if (success) {
                    val publishResult = PublishResult(
                        messageId = resultMap["message_id"] as? String ?: "",
                        timestamp = java.time.Instant.now(),
                        channel = resultMap["channel"] as? String ?: "",
                        success = true
                    )
                    BulkResult(success = true, result = publishResult)
                } else {
                    BulkResult(success = false, error = resultMap["error"] as? String ?: "Unknown error")
                }
            }
            
        } catch (e: Exception) {
            val exception = OddSocketsException.from(e)
            logger.error(e) { "Bulk publish failed" }
            throw exception
        }
    }
    
    /**
     * Adds an event handler.
     * @param eventType The event type
     * @param handler The event handler
     */
    fun on(eventType: EventType, handler: SuspendEventHandler) {
        eventHandlers.computeIfAbsent(eventType) { mutableListOf() }.add(handler)
    }
    
    /**
     * Removes event handlers for a specific event type.
     * @param eventType The event type
     */
    fun off(eventType: EventType) {
        eventHandlers.remove(eventType)
    }
    
    /**
     * Removes all event handlers.
     */
    fun offAll() {
        eventHandlers.clear()
    }
    
    /**
     * Closes the client and releases resources.
     */
    fun close() {
        scope.launch {
            disconnect()
            scope.cancel()
            httpClient.close()
        }
    }
    
    // Internal methods
    
    internal suspend fun sendChannelRequest(request: Map<String, Any?>): Map<String, Any?> {
        return sendRequest(request)
    }
    
    private suspend fun getWorkerAssignment(): WorkerAssignment {
        return try {
            // Step 1: Discover the optimal manager URL automatically
            val managerUrl = ManagerDiscovery.instance.discoverManagerUrl(config.apiKey)
            
            // Step 2: Get worker assignment from manager
            val response = httpClient.get("$managerUrl/api/cluster/select-worker") {
                header("User-Agent", "OddSockets-Kotlin-SDK/1.0.0")
                parameter("apiKey", config.apiKey)
                parameter("userId", userId)
                parameter("clientIdentifier", clientIdentifier)
            }
            
            if (response.status.isSuccess()) {
                val responseBody: String = response.body()
                val responseData = Json.parseToJsonElement(responseBody).jsonObject
                
                val workerUrl = responseData["url"]?.jsonPrimitive?.content
                val workerId = responseData["workerId"]?.jsonPrimitive?.content
                val session = responseData["session"]?.jsonObject
                
                // Store session information
                sessionInfo = session?.let { sessionObj ->
                    sessionObj.mapValues { (_, value) ->
                        when (value) {
                            is JsonPrimitive -> value.contentOrNull ?: value.toString()
                            else -> value.toString()
                        }
                    }
                }
                
                // Emit worker assignment event
                scope.launch {
                    emitEvent(EventType.WORKER_ASSIGNED, mapOf(
                        "workerId" to workerId,
                        "workerUrl" to workerUrl,
                        "session" to sessionInfo,
                        "clientIdentifier" to clientIdentifier,
                        "managerUrl" to managerUrl
                    ))
                }
                
                WorkerAssignment(
                    url = workerUrl,
                    workerId = workerId
                )
            } else {
                throw AuthenticationException("Worker assignment failed: ${response.status}")
            }
            
        } catch (e: Exception) {
            // If manager is offline, try fallback logic
            if (e.message?.contains("ECONNREFUSED") == true || e.message?.contains("ENOTFOUND") == true) {
                throw ConnectionException.workerAssignmentFailed("Manager is offline. Cannot assign worker without session stickiness.")
            }
            throw ConnectionException.workerAssignmentFailed(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun connectToWorker(workerUrl: String) {
        val wsUrl = workerUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws"
        
        try {
            httpClient.webSocket(
                method = HttpMethod.Get,
                host = java.net.URL(wsUrl).host,
                port = java.net.URL(wsUrl).port.takeIf { it != -1 } ?: if (wsUrl.startsWith("wss://")) 443 else 80,
                path = java.net.URL(wsUrl).path
            ) {
                webSocketSession.set(this)
                
                // Send authentication
                send(Json.encodeToString(mapOf(
                    "type" to "auth",
                    "api_key" to config.apiKey,
                    "user_id" to userId
                )))
                
                // Handle incoming messages
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            handleWebSocketMessage(frame.readText())
                        }
                        is Frame.Close -> {
                            logger.info { "WebSocket connection closed" }
                            handleDisconnection()
                            break
                        }
                        else -> {
                            // Ignore other frame types
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw ConnectionException("Failed to connect to worker: ${e.message}", cause = e)
        }
    }
    
    private suspend fun handleWebSocketMessage(messageText: String) {
        try {
            val messageData = Json.parseToJsonElement(messageText).jsonObject
            val type = messageData["type"]?.jsonPrimitive?.content
            
            when (type) {
                "auth_success" -> {
                    logger.debug { "Authentication successful" }
                }
                "auth_error" -> {
                    val error = messageData["error"]?.jsonPrimitive?.content ?: "Authentication failed"
                    throw AuthenticationException(error)
                }
                "message" -> {
                    val message = Json.decodeFromJsonElement<Message>(messageData["data"]!!)
                    handleIncomingMessage(message)
                }
                "presence" -> {
                    val presence = Json.decodeFromJsonElement<PresenceInfo>(messageData["data"]!!)
                    handlePresenceUpdate(presence)
                }
                "error" -> {
                    val error = messageData["error"]?.jsonPrimitive?.content ?: "Unknown error"
                    emitEvent(EventType.ERROR, GenericException(error))
                }
                else -> {
                    logger.debug { "Unknown message type: $type" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling WebSocket message: $messageText" }
            emitEvent(EventType.ERROR, OddSocketsException.from(e))
        }
    }
    
    private suspend fun handleIncomingMessage(message: Message) {
        logger.debug { "Received message on channel ${message.channel}" }
        
        // Emit to global message flow
        _messageFlow.emit(message)
        
        // Forward to specific channel
        channels[message.channel]?.handleMessage(message)
        
        emitEvent(EventType.MESSAGE, message)
    }
    
    private suspend fun handlePresenceUpdate(presence: PresenceInfo) {
        logger.debug { "Presence update for channel ${presence.channel}: ${presence.count} users" }
        
        // Forward to specific channel
        channels[presence.channel]?.handlePresence(presence)
        
        emitEvent(EventType.PRESENCE, presence)
    }
    
    private suspend fun handleDisconnection() {
        if (isConnected) {
            updateConnectionState(ConnectionState.RECONNECTING)
            startReconnection()
        }
    }
    
    private fun startReconnection() {
        if (reconnectAttempts >= config.reconnectAttempts) {
            logger.warn { "Maximum reconnection attempts reached" }
            updateConnectionState(ConnectionState.FAILED)
            scope.launch {
                emitEvent(EventType.MAX_RECONNECT_ATTEMPTS_REACHED, mapOf("attempts" to reconnectAttempts))
            }
            return
        }
        
        reconnectJob = scope.launch {
            val delay = minOf(1000L * (1 shl reconnectAttempts), 30000L) // Exponential backoff, max 30s
            logger.info { "Reconnecting in ${delay}ms (attempt ${reconnectAttempts + 1}/${config.reconnectAttempts})" }
            
            delay(delay)
            
            try {
                reconnectAttempts++
                connect()
                emitEvent(EventType.RECONNECTED, mapOf("attempts" to reconnectAttempts))
            } catch (e: Exception) {
                logger.error(e) { "Reconnection attempt failed" }
                startReconnection()
            }
        }
    }
    
    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                try {
                    sendRequest(mapOf("type" to "ping"))
                    delay(config.heartbeatInterval)
                } catch (e: Exception) {
                    logger.error(e) { "Heartbeat failed" }
                    break
                }
            }
        }
    }
    
    private suspend fun sendRequest(request: Map<String, Any?>): Map<String, Any?> {
        val session = webSocketSession.get() ?: throw ConnectionException("Not connected")
        
        return withTimeout(config.timeout) {
            val requestId = OddSocketsUtils.generateMessageId()
            val requestWithId = request + ("request_id" to requestId)
            
            session.send(Json.encodeToString(requestWithId))
            
            // For simplicity, return empty map. In a real implementation,
            // you'd wait for the response with the matching request_id
            emptyMap()
        }
    }
    
    private fun updateConnectionState(newState: ConnectionState) {
        val oldState = _connectionState.value
        if (oldState != newState) {
            _connectionState.value = newState
            logger.debug { "Connection state changed: $oldState -> $newState" }
        }
    }
    
    private suspend fun emitEvent(eventType: EventType, data: Any?) {
        // Emit to flow
        _eventFlow.emit(eventType to data)
        
        // Call registered handlers
        eventHandlers[eventType]?.forEach { handler ->
            try {
                handler(data)
            } catch (e: Exception) {
                logger.error(e) { "Error in event handler for $eventType" }
            }
        }
    }
    
    private fun validateChannelName(channelName: String) {
        if (channelName.isBlank()) {
            throw ChannelException.invalidChannelName(channelName)
        }
        if (channelName.length > 100) {
            throw ChannelException.invalidChannelName("Channel name too long: ${channelName.length} characters")
        }
        if (!channelName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            throw ChannelException.invalidChannelName("Channel name contains invalid characters")
        }
    }
    
    /**
     * Generate consistent client identifier for session stickiness
     * @return Client identifier
     */
    private fun generateClientIdentifier(): String {
        // Create a consistent identifier based on API key and user ID
        val baseId = config.userId ?: "default"
        val apiKeyHash = hashString(config.apiKey)
        return "${apiKeyHash}_${baseId}"
    }
    
    /**
     * Simple hash function for API key
     * @param str String to hash
     * @return Hash string
     */
    private fun hashString(str: String): String {
        var hash = 0
        if (str.isEmpty()) return hash.toString()
        
        for (char in str) {
            hash = ((hash shl 5) - hash) + char.code
            hash = hash and hash // Convert to 32-bit integer
        }
        return kotlin.math.abs(hash).toString(36)
    }
    
    /**
     * Get current connection state
     * @return Connection state
     */
    fun getState(): ConnectionState {
        return connectionState.value
    }
    
    /**
     * Get assigned worker information
     * @return Worker info or null if not assigned
     */
    fun getWorkerInfo(): Map<String, String?>? {
        val (workerId, workerUrl) = workerInfo.value
        return if (workerId != null && workerUrl != null) {
            mapOf(
                "workerId" to workerId,
                "workerUrl" to workerUrl
            )
        } else null
    }
    
    /**
     * Get client identifier used for session stickiness
     * @return Client identifier
     */
    fun getClientIdentifier(): String {
        return clientIdentifier
    }
    
    /**
     * Get session information
     * @return Session info or null if not available
     */
    fun getSessionInfo(): Map<String, Any?>? {
        return sessionInfo
    }
    
    companion object {
        /**
         * Creates a client with default configuration.
         * @param apiKey The API key
         * @return A configured client instance
         */
        fun default(apiKey: String): OddSocketsClient {
            return OddSocketsClient(OddSocketsConfig.default(apiKey))
        }
        
        /**
         * Creates a client with builder configuration.
         * @param apiKey The API key
         * @param block The configuration block
         * @return A configured client instance
         */
        inline fun create(apiKey: String, block: com.oddsockets.config.OddSocketsConfigBuilder.() -> Unit = {}): OddSocketsClient {
            val config = com.oddsockets.config.OddSocketsConfig.builder(apiKey).apply(block).build()
            return OddSocketsClient(config)
        }
    }
}
