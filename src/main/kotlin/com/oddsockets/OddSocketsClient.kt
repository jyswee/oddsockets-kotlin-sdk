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
    private var readerJob: Job? = null
    private var reconnectAttempts = 0

    // Set while a caller-initiated disconnect is tearing the connection down, so
    // the reader's teardown does not misfire the reconnection loop.
    @Volatile
    private var intentionalDisconnect = false

    // Socket.IO handshake gate + in-flight request correlation.
    private var connectAck: CompletableDeferred<Unit>? = null
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    
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
        intentionalDisconnect = false
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
        intentionalDisconnect = true

        // Cancel reconnection attempts
        reconnectJob?.cancel()
        reconnectJob = null
        
        // Stop heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = null

        // Stop the frame reader
        readerJob?.cancel()
        readerJob = null

        // Fail any in-flight requests so callers don't hang
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()

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
        val uri = java.net.URI(workerUrl)
        val secure = uri.scheme == "https" || uri.scheme == "wss"
        val resolvedPort = when {
            uri.port != -1 -> uri.port
            secure -> 443
            else -> 80
        }

        val ack = CompletableDeferred<Unit>()
        connectAck = ack

        try {
            // The OddSockets worker speaks Socket.IO (Engine.IO v4), so we open the
            // WebSocket on the Socket.IO endpoint and drive the handshake by hand.
            val session = httpClient.webSocketSession {
                url {
                    protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
                    host = uri.host
                    port = resolvedPort
                    encodedPath = "/socket.io/"
                    parameters.append("EIO", "4")
                    parameters.append("transport", "websocket")
                }
            }
            webSocketSession.set(session)

            // Read frames on a background coroutine so the handshake and every
            // subsequent event are processed without blocking the caller.
            readerJob = scope.launch {
                try {
                    for (frame in session.incoming) {
                        if (frame is Frame.Text) {
                            handleEngineFrame(frame.readText())
                        }
                    }
                } catch (e: CancellationException) {
                    // Reader cancelled by an intentional disconnect - not an error.
                } catch (e: Exception) {
                    logger.debug(e) { "WebSocket reader stopped" }
                } finally {
                    handleDisconnection()
                }
            }

            // Block until the worker acknowledges the Socket.IO CONNECT.
            withTimeout(config.timeout) { ack.await() }
        } catch (e: TimeoutCancellationException) {
            throw ConnectionException("Timed out waiting for worker Socket.IO handshake")
        } catch (e: AuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw ConnectionException("Failed to connect to worker: ${e.message}", cause = e)
        }
    }

    /**
     * Handles a single Engine.IO frame (the first character is the packet type).
     */
    private suspend fun handleEngineFrame(text: String) {
        if (text.isEmpty()) return
        when (text[0]) {
            '0' -> {
                // OPEN — reply with a Socket.IO CONNECT carrying the auth payload.
                // The worker reads apiKey/userId off socket.handshake.auth.
                val auth = buildJsonObject {
                    put("apiKey", config.apiKey)
                    put("userId", userId)
                }
                webSocketSession.get()?.send(Frame.Text("40" + auth.toString()))
            }
            '2' -> {
                // PING — keep the connection alive with a PONG.
                webSocketSession.get()?.send(Frame.Text("3"))
            }
            '4' -> handleSocketMessage(text.substring(1))
            else -> {
                // 3 = PONG, 1 = CLOSE, 6 = NOOP — nothing to do.
            }
        }
    }

    /**
     * Handles a Socket.IO packet (body is everything after the Engine.IO MESSAGE type).
     */
    private suspend fun handleSocketMessage(body: String) {
        if (body.isEmpty()) return
        when (body[0]) {
            '0' -> connectAck?.complete(Unit)          // CONNECT acknowledged
            '1' -> handleDisconnection()               // DISCONNECT
            '2' -> {                                    // EVENT: 2[<ackId>]["event", payload]
                val jsonStart = body.indexOf('[')
                if (jsonStart == -1) return
                val array = try {
                    Json.parseToJsonElement(body.substring(jsonStart)).jsonArray
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse Socket.IO event: $body" }
                    return
                }
                val event = array.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return
                dispatchSocketEvent(event, array.getOrNull(1))
            }
            '4' -> {                                    // CONNECT_ERROR (usually auth)
                val jsonStart = body.indexOf('{')
                val message = if (jsonStart != -1) {
                    runCatching {
                        Json.parseToJsonElement(body.substring(jsonStart))
                            .jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                } else null
                connectAck?.completeExceptionally(
                    AuthenticationException(message ?: "Connection rejected by worker")
                )
            }
            else -> {
                // ACK (3) and binary packets are not used by this SDK.
            }
        }
    }

    /**
     * Routes a decoded Socket.IO event either to the correlated request waiter or
     * to the broadcast handlers (messages / presence changes / errors).
     */
    private suspend fun dispatchSocketEvent(event: String, payload: JsonElement?) {
        val obj = payload as? JsonObject
        when (event) {
            "message" -> if (obj != null) handleIncomingMessage(messageFromWorker(obj))
            "presence_change" -> {
                val channelName = obj?.get("channel")?.jsonPrimitive?.contentOrNull
                val occupancy = obj?.get("occupancy")?.jsonPrimitive?.intOrNull ?: 0
                if (channelName != null) {
                    handlePresenceUpdate(PresenceInfo(channelName, emptyList(), occupancy))
                }
            }
            "error" -> {
                val type = obj?.get("type")?.jsonPrimitive?.contentOrNull ?: "ERROR"
                val message = obj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                emitEvent(EventType.ERROR, GenericException("$type: $message"))
            }
            "subscribed", "unsubscribed", "published", "presence", "history" -> {
                val channelName = obj?.get("channel")?.jsonPrimitive?.contentOrNull ?: ""
                pendingResponses.remove("$event:$channelName")?.complete(obj ?: JsonObject(emptyMap()))
            }
            else -> logger.debug { "Unhandled Socket.IO event: $event" }
        }
    }

    /**
     * Adapts the worker's broadcast message envelope into the SDK Message model.
     * The worker nests the user payload under "message" and the sender under
     * "publisher.userId".
     */
    private fun messageFromWorker(obj: JsonObject): Message {
        val publisher = obj["publisher"] as? JsonObject
        return Message(
            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: OddSocketsUtils.generateMessageId(),
            channel = obj["channel"]?.jsonPrimitive?.contentOrNull ?: "",
            data = obj["message"],
            userId = publisher?.get("userId")?.jsonPrimitive?.contentOrNull,
            metadata = obj["metadata"] as? JsonObject
        )
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
        // A caller-initiated disconnect tears things down itself; don't reconnect.
        if (intentionalDisconnect) return
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
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error(e) { "Heartbeat failed" }
                    break
                }
            }
        }
    }
    
    private suspend fun sendRequest(request: Map<String, Any?>): Map<String, Any?> {
        val session = webSocketSession.get() ?: throw ConnectionException("Not connected")
        val type = request["type"] as? String ?: throw GenericException("Request is missing a type")

        // Socket.IO maintains its own Engine.IO heartbeat, so an application-level
        // ping is a no-op here.
        if (type == "ping") return emptyMap()

        val channelName = request["channel"] as? String
        val (emitName, responseName) = when (type) {
            "subscribe" -> "subscribe" to "subscribed"
            "unsubscribe" -> "unsubscribe" to "unsubscribed"
            "publish" -> "publish" to "published"
            "get_presence" -> "get_presence" to "presence"
            "get_history" -> "get_history" to "history"
            else -> throw GenericException("Unsupported request type: $type")
        }

        // Build the Socket.IO event payload from the request (minus the routing
        // key). Null values are omitted so the worker's own destructuring
        // defaults apply (e.g. `options = {}`) instead of receiving JSON null.
        val payload = buildJsonObject {
            for ((key, value) in request) {
                if (key == "type" || value == null) continue
                put(key, anyToJson(value))
            }
        }

        val correlationKey = "$responseName:${channelName ?: ""}"
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponses[correlationKey] = deferred

        return try {
            withTimeout(config.timeout) {
                val encoded = JsonArray(listOf(JsonPrimitive(emitName), payload)).toString()
                session.send(Frame.Text("42$encoded"))
                adaptResponse(type, deferred.await())
            }
        } catch (e: TimeoutCancellationException) {
            throw TimeoutException.operationTimeout(type, config.timeout.inWholeSeconds)
        } finally {
            pendingResponses.remove(correlationKey)
        }
    }

    /**
     * Recursively converts the loosely-typed request maps that channels build into
     * JSON, so they can be encoded into a Socket.IO event.
     */
    private fun anyToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), anyToJson(v)) } }
        is List<*> -> buildJsonArray { value.forEach { add(anyToJson(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    /**
     * Adapts a worker response event into the map shape the channel layer expects.
     * The worker uses "messageId"/"occupancy"/"occupants"; channels read
     * "message_id"/"presence"(count, users).
     */
    private fun adaptResponse(type: String, payload: JsonObject): Map<String, Any?> = when (type) {
        "publish" -> mapOf(
            "success" to true,
            "message_id" to (payload["messageId"]?.jsonPrimitive?.contentOrNull
                ?: payload["message_id"]?.jsonPrimitive?.contentOrNull)
        )
        "get_presence" -> {
            val occupancy = payload["occupancy"]?.jsonPrimitive?.intOrNull
                ?: payload["count"]?.jsonPrimitive?.intOrNull ?: 0
            val occupants = payload["occupants"] as? JsonArray ?: JsonArray(emptyList())
            val users = occupants.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull
                    is JsonObject -> (el["userId"] ?: el["user_id"] ?: el["id"])
                        ?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            val presence = PresenceInfo(
                channel = payload["channel"]?.jsonPrimitive?.contentOrNull ?: "",
                users = users,
                count = occupancy
            )
            mapOf("success" to true, "presence" to presence)
        }
        "get_history" -> mapOf(
            "success" to true,
            "messages" to ((payload["messages"] as? JsonArray)?.toList() ?: emptyList<JsonElement>())
        )
        else -> mapOf("success" to true)
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
