package com.oddsockets

import com.oddsockets.exception.*
import com.oddsockets.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents a channel for real-time messaging.
 *
 * This class provides methods for subscribing to channels, publishing messages,
 * retrieving message history, and managing presence information.
 *
 * @property channelName The name of the channel
 * @property client The parent OddSockets client
 * @property scope The coroutine scope for this channel
 */
class OddSocketsChannel internal constructor(
    val channelName: String,
    private val client: OddSocketsClient,
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    
    // Subscription state
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    
    private val _subscriptionOptions = MutableStateFlow<SubscribeOptions?>(null)
    val subscriptionOptions: StateFlow<SubscribeOptions?> = _subscriptionOptions.asStateFlow()
    
    // Message handling
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow: SharedFlow<Message> = _messageFlow.asSharedFlow()
    
    private val messageHandlers = ConcurrentHashMap<String, SuspendMessageHandler>()
    private var defaultMessageHandler: SuspendMessageHandler? = null
    
    // Presence handling
    private val _presenceInfo = MutableStateFlow<PresenceInfo?>(null)
    val presenceInfo: StateFlow<PresenceInfo?> = _presenceInfo.asStateFlow()
    
    private val _presenceFlow = MutableSharedFlow<PresenceInfo>()
    val presenceFlow: SharedFlow<PresenceInfo> = _presenceFlow.asSharedFlow()
    
    // Event handling
    private val eventHandlers = ConcurrentHashMap<EventType, MutableList<SuspendEventHandler>>()
    private val _eventFlow = MutableSharedFlow<Pair<EventType, Any?>>()
    val eventFlow: SharedFlow<Pair<EventType, Any?>> = _eventFlow.asSharedFlow()
    
    // Message history
    private val _messageHistory = MutableStateFlow<List<Message>>(emptyList())
    val messageHistory: StateFlow<List<Message>> = _messageHistory.asStateFlow()
    
    /**
     * Subscribes to the channel.
     * @param handler The message handler
     * @param options The subscription options
     * @throws ChannelException if subscription fails
     */
    suspend fun subscribe(
        handler: SuspendMessageHandler,
        options: SubscribeOptions = SubscribeOptions()
    ) {
        if (_isSubscribed.value) {
            logger.debug { "Already subscribed to channel: $channelName" }
            return
        }
        
        logger.info { "Subscribing to channel: $channelName" }
        
        try {
            val request = mapOf(
                "type" to "subscribe",
                "channel" to channelName,
                "options" to mapOf(
                    "enable_presence" to options.enablePresence,
                    "retain_history" to options.retainHistory,
                    "filter_expression" to options.filterExpression
                )
            )
            
            val response = client.sendChannelRequest(request)
            
            if (response["success"] == true) {
                _isSubscribed.value = true
                _subscriptionOptions.value = options
                defaultMessageHandler = handler
                
                emitEvent(EventType.CONNECTED, mapOf("channel" to channelName))
                logger.info { "Successfully subscribed to channel: $channelName" }
                
                // Load initial history if enabled
                if (options.retainHistory) {
                    loadInitialHistory()
                }
                
                // Load initial presence if enabled
                if (options.enablePresence) {
                    loadInitialPresence()
                }
            } else {
                val error = response["error"] as? String ?: "Subscription failed"
                throw ChannelException.subscriptionFailed(channelName, error)
            }
            
        } catch (e: Exception) {
            val exception = when (e) {
                is OddSocketsException -> e
                else -> ChannelException.subscriptionFailed(channelName, e.message ?: "Unknown error")
            }
            emitEvent(EventType.ERROR, exception)
            throw exception
        }
    }
    
    /**
     * Unsubscribes from the channel.
     */
    suspend fun unsubscribe() {
        if (!_isSubscribed.value) {
            logger.debug { "Not subscribed to channel: $channelName" }
            return
        }
        
        logger.info { "Unsubscribing from channel: $channelName" }
        
        try {
            val request = mapOf(
                "type" to "unsubscribe",
                "channel" to channelName
            )
            
            client.sendChannelRequest(request)
            
            _isSubscribed.value = false
            _subscriptionOptions.value = null
            defaultMessageHandler = null
            _presenceInfo.value = null
            
            emitEvent(EventType.DISCONNECTED, mapOf("channel" to channelName))
            logger.info { "Successfully unsubscribed from channel: $channelName" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error unsubscribing from channel: $channelName" }
            emitEvent(EventType.ERROR, OddSocketsException.from(e))
        }
    }
    
    /**
     * Publishes a message to the channel.
     * @param message The message to publish
     * @param options The publish options
     * @return The publish result
     * @throws MessageException if publishing fails
     */
    suspend fun publish(
        message: JsonElement?,
        options: PublishOptions? = null
    ): PublishResult {
        logger.debug { "Publishing message to channel: $channelName" }
        
        try {
            // Validate message size before publishing
            MessageSizeValidator.validateMessageSize(message)
            
            val request = mapOf(
                "type" to "publish",
                "channel" to channelName,
                "message" to message,
                "options" to options?.let { opts ->
                    mapOf(
                        "ttl" to opts.ttl,
                        "metadata" to opts.metadata,
                        "store_in_history" to opts.storeInHistory
                    )
                }
            )
            
            val response = client.sendChannelRequest(request)
            
            if (response["success"] == true) {
                val result = PublishResult(
                    messageId = response["message_id"] as? String ?: OddSocketsUtils.generateMessageId(),
                    timestamp = java.time.Instant.now(),
                    channel = channelName,
                    success = true
                )
                
                logger.debug { "Message published successfully: ${result.messageId}" }
                return result
            } else {
                val error = response["error"] as? String ?: "Publish failed"
                throw MessageException.deliveryFailed("unknown", error)
            }
            
        } catch (e: Exception) {
            val exception = when (e) {
                is OddSocketsException -> e
                else -> MessageException.deliveryFailed("unknown", e.message ?: "Unknown error")
            }
            logger.error(e) { "Failed to publish message to channel: $channelName" }
            throw exception
        }
    }
    
    /**
     * Publishes a string message to the channel.
     * @param message The string message
     * @param options The publish options
     * @return The publish result
     */
    suspend fun publish(
        message: String,
        options: PublishOptions? = null
    ): PublishResult = publish(JsonPrimitive(message), options)
    
    /**
     * Publishes an object message to the channel.
     * @param message The object message
     * @param options The publish options
     * @return The publish result
     */
    suspend inline fun <reified T> publish(
        message: T,
        options: PublishOptions? = null
    ): PublishResult = publish(Json.encodeToJsonElement(message), options)
    
    /**
     * Retrieves message history for the channel.
     * @param options The history options
     * @return The list of historical messages
     * @throws ChannelException if history retrieval fails
     */
    suspend fun getHistory(options: HistoryOptions? = null): List<Message> {
        logger.debug { "Retrieving history for channel: $channelName" }
        
        try {
            val request = mapOf(
                "type" to "get_history",
                "channel" to channelName,
                "options" to options?.let { opts ->
                    mapOf(
                        "limit" to opts.limit,
                        "start" to opts.start?.toString(),
                        "end" to opts.end?.toString(),
                        "reverse" to opts.reverse
                    )
                }
            )
            
            val response = client.sendChannelRequest(request)
            
            if (response["success"] == true) {
                val messagesData = response["messages"] as? List<*> ?: emptyList<Any>()
                val messages = messagesData.mapNotNull { messageData ->
                    try {
                        Json.decodeFromJsonElement<Message>(Json.encodeToJsonElement(messageData))
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to decode message from history" }
                        null
                    }
                }
                
                logger.debug { "Retrieved ${messages.size} messages from history" }
                return messages
            } else {
                val error = response["error"] as? String ?: "History retrieval failed"
                throw ChannelException("Failed to retrieve history for channel $channelName: $error", channelName)
            }
            
        } catch (e: Exception) {
            val exception = when (e) {
                is OddSocketsException -> e
                else -> ChannelException("History retrieval failed: ${e.message}", channelName, cause = e)
            }
            logger.error(e) { "Failed to retrieve history for channel: $channelName" }
            throw exception
        }
    }
    
    /**
     * Gets current presence information for the channel.
     * @return The presence information
     * @throws ChannelException if presence retrieval fails
     */
    suspend fun getPresence(): PresenceInfo {
        logger.debug { "Getting presence for channel: $channelName" }
        
        try {
            val request = mapOf(
                "type" to "get_presence",
                "channel" to channelName
            )
            
            val response = client.sendChannelRequest(request)
            
            if (response["success"] == true) {
                val presenceData = response["presence"] ?: throw ChannelException("No presence data in response", channelName)
                val presence = Json.decodeFromJsonElement<PresenceInfo>(Json.encodeToJsonElement(presenceData))
                
                _presenceInfo.value = presence
                logger.debug { "Retrieved presence for channel $channelName: ${presence.count} users" }
                return presence
            } else {
                val error = response["error"] as? String ?: "Presence retrieval failed"
                throw ChannelException("Failed to get presence for channel $channelName: $error", channelName)
            }
            
        } catch (e: Exception) {
            val exception = when (e) {
                is OddSocketsException -> e
                else -> ChannelException("Presence retrieval failed: ${e.message}", channelName, cause = e)
            }
            logger.error(e) { "Failed to get presence for channel: $channelName" }
            throw exception
        }
    }
    
    /**
     * Adds an event handler for this channel.
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
    
    // Internal methods called by the client
    
    internal suspend fun handleMessage(message: Message) {
        logger.debug { "Handling message for channel: $channelName" }
        
        // Add to history if enabled
        val options = _subscriptionOptions.value
        if (options?.retainHistory == true) {
            val currentHistory = _messageHistory.value.toMutableList()
            currentHistory.add(message)
            
            // Limit history size
            if (currentHistory.size > Constants.MAX_MESSAGE_HISTORY_SIZE) {
                currentHistory.removeAt(0)
            }
            
            _messageHistory.value = currentHistory
        }
        
        // Emit to flow
        _messageFlow.emit(message)
        
        // Call message handlers
        try {
            defaultMessageHandler?.invoke(message)
        } catch (e: Exception) {
            logger.error(e) { "Error in message handler for channel: $channelName" }
        }
        
        emitEvent(EventType.MESSAGE, message)
    }
    
    internal suspend fun handlePresence(presence: PresenceInfo) {
        logger.debug { "Handling presence update for channel: $channelName" }
        
        _presenceInfo.value = presence
        _presenceFlow.emit(presence)
        
        emitEvent(EventType.PRESENCE, presence)
    }
    
    internal fun handleDisconnection() {
        logger.debug { "Handling disconnection for channel: $channelName" }
        
        _isSubscribed.value = false
        _presenceInfo.value = null
        
        scope.launch {
            emitEvent(EventType.DISCONNECTED, mapOf("channel" to channelName))
        }
    }
    
    private suspend fun loadInitialHistory() {
        try {
            val history = getHistory(HistoryOptions.recent(50))
            _messageHistory.value = history
            logger.debug { "Loaded ${history.size} messages from initial history" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load initial history for channel: $channelName" }
        }
    }
    
    private suspend fun loadInitialPresence() {
        try {
            getPresence()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load initial presence for channel: $channelName" }
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
                logger.error(e) { "Error in event handler for $eventType on channel: $channelName" }
            }
        }
    }
}

// Extension functions for convenient usage

/**
 * Subscribes to a channel with DSL-style options.
 */
suspend inline fun OddSocketsChannel.subscribe(
    noinline handler: SuspendMessageHandler,
    block: SubscribeOptionsBuilder.() -> Unit = {}
): Unit = subscribe(handler, subscribeOptions(block))

/**
 * Publishes a message with DSL-style options.
 */
suspend inline fun OddSocketsChannel.publish(
    message: JsonElement?,
    block: PublishOptionsBuilder.() -> Unit = {}
): PublishResult = publish(message, publishOptions(block))

/**
 * Publishes a string message with DSL-style options.
 */
suspend inline fun OddSocketsChannel.publish(
    message: String,
    block: PublishOptionsBuilder.() -> Unit = {}
): PublishResult = publish(message, publishOptions(block))

/**
 * Publishes an object message with DSL-style options.
 */
suspend inline fun <reified T> OddSocketsChannel.publish(
    message: T,
    block: PublishOptionsBuilder.() -> Unit = {}
): PublishResult = publish(message, publishOptions(block))

/**
 * Gets history with DSL-style options.
 */
suspend inline fun OddSocketsChannel.getHistory(
    block: HistoryOptionsBuilder.() -> Unit = {}
): List<Message> = getHistory(historyOptions(block))
