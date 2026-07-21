package com.oddsockets.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant
import java.util.*

/**
 * Represents a message received from OddSockets.
 *
 * This data class encapsulates all information about a message including its content,
 * metadata, and routing information. It provides Kotlin-idiomatic access patterns
 * and serialization support.
 *
 * @property id The unique message identifier
 * @property channel The channel name
 * @property data The message payload as JsonElement for flexible typing
 * @property timestamp The message timestamp as Instant
 * @property userId The sender's user ID
 * @property metadata Additional message metadata
 */
@Serializable
data class Message(
    val id: String,
    val channel: String,
    val data: JsonElement? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    @SerialName("user_id")
    val userId: String? = null,
    val metadata: Map<String, JsonElement>? = null
) {
    
    /**
     * Convenience method to get data as a specific type.
     * @param T The target type
     * @return The data cast to type T, or null if casting fails
     */
    inline fun <reified T> dataAs(): T? {
        val element = data ?: return null
        return try {
            Json.decodeFromJsonElement<T>(element)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convenience method to get data as String.
     * @return The data as String, or null if not a string
     */
    fun dataAsString(): String? = dataAs<String>()
    
    /**
     * Convenience method to get data as Map.
     * @return The data as Map<String, JsonElement>, or null if not a map
     */
    fun dataAsMap(): Map<String, JsonElement>? = dataAs<Map<String, JsonElement>>()
    
    /**
     * Convenience method to get data as List.
     * @return The data as List<JsonElement>, or null if not a list
     */
    fun dataAsList(): List<JsonElement>? = dataAs<List<JsonElement>>()
    
    /**
     * Gets a metadata value by key.
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    fun getMetadata(key: String): JsonElement? = metadata?.get(key)
    
    /**
     * Gets a metadata value as a specific type.
     * @param key The metadata key
     * @param T The target type
     * @return The metadata value cast to type T, or null if not found or casting fails
     */
    inline fun <reified T> getMetadataAs(key: String): T? {
        val element = metadata?.get(key) ?: return null
        return try {
            Json.decodeFromJsonElement<T>(element)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Checks if this message has metadata.
     * @return true if metadata is not null and not empty
     */
    fun hasMetadata(): Boolean = !metadata.isNullOrEmpty()
    
    /**
     * Checks if this message has data.
     * @return true if data is not null
     */
    fun hasData(): Boolean = data != null
    
    companion object {
        /**
         * Creates a new message with generated ID.
         * @param channel The channel name
         * @param data The message data
         * @param userId The user ID
         * @param metadata The metadata
         * @return A new Message instance
         */
        fun create(
            channel: String,
            data: JsonElement? = null,
            userId: String? = null,
            metadata: Map<String, JsonElement>? = null
        ): Message = Message(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").lowercase()}",
            channel = channel,
            data = data,
            userId = userId,
            metadata = metadata
        )
    }
}

/**
 * Represents presence information for a channel.
 *
 * @property channel The channel name
 * @property users The list of user IDs present in the channel
 * @property count The total number of users present
 * @property timestamp When the presence snapshot was taken
 */
@Serializable
data class PresenceInfo(
    val channel: String,
    val users: List<String>,
    val count: Int,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now()
) {
    
    /**
     * Checks if a user is present in the channel.
     * @param userId The user ID to check
     * @return true if the user is present
     */
    fun isUserPresent(userId: String): Boolean = users.contains(userId)
    
    /**
     * Checks if the channel is empty.
     * @return true if no users are present
     */
    fun isEmpty(): Boolean = count == 0 || users.isEmpty()
    
    /**
     * Gets the presence as a percentage of a maximum capacity.
     * @param maxCapacity The maximum capacity
     * @return The presence percentage (0.0 to 1.0)
     */
    fun getPresenceRatio(maxCapacity: Int): Double = 
        if (maxCapacity <= 0) 0.0 else count.toDouble() / maxCapacity.toDouble()
}

/**
 * Represents the result of a publish operation.
 *
 * @property messageId The unique identifier of the published message
 * @property timestamp When the message was published
 * @property channel The channel the message was published to
 * @property success Whether the publish was successful
 */
@Serializable
data class PublishResult(
    @SerialName("message_id")
    val messageId: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val channel: String,
    val success: Boolean
) {
    
    /**
     * Checks if the publish operation was successful.
     * @return true if successful
     */
    fun isSuccessful(): Boolean = success
    
    /**
     * Checks if the publish operation failed.
     * @return true if failed
     */
    fun isFailed(): Boolean = !success
}

/**
 * Represents a message for bulk publishing.
 *
 * @property channel The channel name
 * @property message The message payload
 * @property options The publish options for this message
 */
@Serializable
data class BulkMessage(
    val channel: String,
    val message: JsonElement? = null,
    val options: PublishOptions? = null
) {
    
    companion object {
        /**
         * Creates a bulk message with string data.
         * @param channel The channel name
         * @param message The string message
         * @param options The publish options
         * @return A new BulkMessage instance
         */
        fun text(
            channel: String,
            message: String,
            options: PublishOptions? = null
        ): BulkMessage = BulkMessage(
            channel = channel,
            message = kotlinx.serialization.json.JsonPrimitive(message),
            options = options
        )
        
        /**
         * Creates a bulk message with object data.
         * @param channel The channel name
         * @param message The object message
         * @param options The publish options
         * @return A new BulkMessage instance
         */
        inline fun <reified T> obj(
            channel: String,
            message: T,
            options: PublishOptions? = null
        ): BulkMessage = BulkMessage(
            channel = channel,
            message = Json.encodeToJsonElement(message),
            options = options
        )
    }
}

/**
 * Represents the result of a bulk publish operation.
 *
 * @property success Whether the publish was successful
 * @property result The publish result if successful
 * @property error The error message if unsuccessful
 */
@Serializable
data class BulkResult(
    val success: Boolean,
    val result: PublishResult? = null,
    val error: String? = null
) {
    
    /**
     * Checks if the bulk publish was successful.
     * @return true if successful
     */
    fun isSuccessful(): Boolean = success
    
    /**
     * Checks if the bulk publish failed.
     * @return true if failed
     */
    fun isFailed(): Boolean = !success
    
    /**
     * Gets the error message or a default message.
     * @param defaultMessage The default error message
     * @return The error message
     */
    fun getErrorMessage(defaultMessage: String = "Unknown error"): String = 
        error ?: defaultMessage
}

/**
 * Options for channel subscription.
 *
 * @property enablePresence Whether to enable presence tracking for the channel
 * @property retainHistory Whether to retain message history
 * @property filterExpression A filter expression for messages
 */
@Serializable
data class SubscribeOptions(
    @SerialName("enable_presence")
    val enablePresence: Boolean = false,
    @SerialName("retain_history")
    val retainHistory: Boolean = false,
    @SerialName("filter_expression")
    val filterExpression: String? = null
) {
    
    companion object {
        /**
         * Creates options with presence enabled.
         */
        val WithPresence = SubscribeOptions(enablePresence = true)
        
        /**
         * Creates options with history enabled.
         */
        val WithHistory = SubscribeOptions(retainHistory = true)
        
        /**
         * Creates options with both presence and history enabled.
         */
        val WithPresenceAndHistory = SubscribeOptions(enablePresence = true, retainHistory = true)
        
        /**
         * Creates options for a chat channel.
         */
        val ChatChannel = SubscribeOptions(enablePresence = true, retainHistory = true)
        
        /**
         * Creates options for a notification channel.
         */
        val NotificationChannel = SubscribeOptions(enablePresence = false, retainHistory = false)
        
        /**
         * Creates options for a data channel.
         */
        val DataChannel = SubscribeOptions(enablePresence = false, retainHistory = true)
    }
}

/**
 * Builder for SubscribeOptions.
 */
class SubscribeOptionsBuilder {
    private var enablePresence: Boolean = false
    private var retainHistory: Boolean = false
    private var filterExpression: String? = null
    
    /**
     * Enables presence tracking.
     * @param enable Whether to enable presence tracking
     * @return The builder instance for chaining
     */
    fun enablePresence(enable: Boolean = true): SubscribeOptionsBuilder = apply {
        this.enablePresence = enable
    }
    
    /**
     * Enables history retention.
     * @param retain Whether to retain history
     * @return The builder instance for chaining
     */
    fun retainHistory(retain: Boolean = true): SubscribeOptionsBuilder = apply {
        this.retainHistory = retain
    }
    
    /**
     * Sets a filter expression.
     * @param expression The filter expression
     * @return The builder instance for chaining
     */
    fun filterExpression(expression: String): SubscribeOptionsBuilder = apply {
        this.filterExpression = expression
    }
    
    /**
     * Sets options for a chat channel.
     * @return The builder instance for chaining
     */
    fun chatChannel(): SubscribeOptionsBuilder = apply {
        enablePresence = true
        retainHistory = true
    }
    
    /**
     * Sets options for a notification channel.
     * @return The builder instance for chaining
     */
    fun notificationChannel(): SubscribeOptionsBuilder = apply {
        enablePresence = false
        retainHistory = false
    }
    
    /**
     * Sets options for a data channel.
     * @return The builder instance for chaining
     */
    fun dataChannel(): SubscribeOptionsBuilder = apply {
        enablePresence = false
        retainHistory = true
    }
    
    /**
     * Builds the options.
     * @return The configured SubscribeOptions instance
     */
    fun build(): SubscribeOptions = SubscribeOptions(
        enablePresence = enablePresence,
        retainHistory = retainHistory,
        filterExpression = filterExpression
    )
}

/**
 * Options for message publishing.
 *
 * @property ttl The time to live for the message in seconds
 * @property metadata Additional metadata for the message
 * @property storeInHistory Whether the message should be stored in history
 */
@Serializable
data class PublishOptions(
    val ttl: Int? = null,
    val metadata: Map<String, JsonElement>? = null,
    @SerialName("store_in_history")
    val storeInHistory: Boolean = false
) {
    
    companion object {
        /**
         * Creates options with history storage enabled.
         */
        val WithHistory = PublishOptions(storeInHistory = true)
        
        /**
         * Creates options with a TTL.
         * @param seconds TTL in seconds
         * @return PublishOptions with TTL set
         */
        fun withTTL(seconds: Int) = PublishOptions(ttl = seconds)
        
        /**
         * Creates options for a chat message.
         */
        val ChatMessage = PublishOptions(
            storeInHistory = true,
            metadata = mapOf("type" to kotlinx.serialization.json.JsonPrimitive("chat"))
        )
        
        /**
         * Creates options for a system message.
         */
        val SystemMessage = PublishOptions(
            storeInHistory = true,
            metadata = mapOf(
                "type" to kotlinx.serialization.json.JsonPrimitive("system"),
                "priority" to kotlinx.serialization.json.JsonPrimitive("high")
            )
        )
    }
}

/**
 * Builder for PublishOptions.
 */
class PublishOptionsBuilder {
    private var ttl: Int? = null
    private var metadata: MutableMap<String, JsonElement>? = null
    private var storeInHistory: Boolean = false
    
    /**
     * Sets the time to live.
     * @param ttl The TTL in seconds
     * @return The builder instance for chaining
     */
    fun ttl(ttl: Int): PublishOptionsBuilder = apply {
        this.ttl = ttl
    }
    
    /**
     * Sets metadata.
     * @param metadata The metadata map
     * @return The builder instance for chaining
     */
    fun metadata(metadata: Map<String, JsonElement>): PublishOptionsBuilder = apply {
        this.metadata = metadata.toMutableMap()
    }
    
    /**
     * Adds a metadata entry.
     * @param key The metadata key
     * @param value The metadata value
     * @return The builder instance for chaining
     */
    fun metadata(key: String, value: JsonElement): PublishOptionsBuilder = apply {
        if (this.metadata == null) {
            this.metadata = mutableMapOf()
        }
        this.metadata!![key] = value
    }
    
    /**
     * Adds a string metadata entry.
     * @param key The metadata key
     * @param value The metadata value
     * @return The builder instance for chaining
     */
    fun metadata(key: String, value: String): PublishOptionsBuilder = apply {
        metadata(key, kotlinx.serialization.json.JsonPrimitive(value))
    }
    
    /**
     * Sets whether to store in history.
     * @param store Whether to store in history
     * @return The builder instance for chaining
     */
    fun storeInHistory(store: Boolean = true): PublishOptionsBuilder = apply {
        this.storeInHistory = store
    }
    
    /**
     * Sets options for a chat message.
     * @return The builder instance for chaining
     */
    fun chatMessage(): PublishOptionsBuilder = apply {
        storeInHistory = true
        metadata("type", "chat")
    }
    
    /**
     * Sets options for a notification message.
     * @param priority The notification priority
     * @param ttlSeconds Time to live in seconds
     * @return The builder instance for chaining
     */
    fun notification(priority: String = "normal", ttlSeconds: Int = 3600): PublishOptionsBuilder = apply {
        ttl = ttlSeconds
        metadata("type", "notification")
        metadata("priority", priority)
    }
    
    /**
     * Sets options for a system message.
     * @return The builder instance for chaining
     */
    fun systemMessage(): PublishOptionsBuilder = apply {
        storeInHistory = true
        metadata("type", "system")
        metadata("priority", "high")
    }
    
    /**
     * Builds the options.
     * @return The configured PublishOptions instance
     */
    fun build(): PublishOptions = PublishOptions(
        ttl = ttl,
        metadata = metadata,
        storeInHistory = storeInHistory
    )
}

/**
 * Options for retrieving message history.
 *
 * @property limit The maximum number of messages to retrieve
 * @property start The start time for the history query
 * @property end The end time for the history query
 * @property reverse Whether messages should be returned in reverse chronological order
 */
@Serializable
data class HistoryOptions(
    val limit: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val start: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val end: Instant? = null,
    val reverse: Boolean = false
) {
    
    companion object {
        /**
         * Creates options with a limit.
         * @param count Maximum number of messages
         * @return HistoryOptions with limit set
         */
        fun limit(count: Int) = HistoryOptions(limit = count)
        
        /**
         * Creates options for recent messages in reverse order.
         * @param count Maximum number of messages
         * @return HistoryOptions for recent messages
         */
        fun recent(count: Int) = HistoryOptions(limit = count, reverse = true)
        
        /**
         * Creates options for the last hour.
         * @param count Maximum number of messages
         * @return HistoryOptions for the last hour
         */
        fun lastHour(count: Int = 100) = HistoryOptions(
            limit = count,
            start = Instant.now().minusSeconds(3600),
            reverse = true
        )
        
        /**
         * Creates options for the last day.
         * @param count Maximum number of messages
         * @return HistoryOptions for the last day
         */
        fun lastDay(count: Int = 1000) = HistoryOptions(
            limit = count,
            start = Instant.now().minusSeconds(86400),
            reverse = true
        )
    }
}

/**
 * Builder for HistoryOptions.
 */
class HistoryOptionsBuilder {
    private var limit: Int? = null
    private var start: Instant? = null
    private var end: Instant? = null
    private var reverse: Boolean = false
    
    /**
     * Sets the limit.
     * @param limit The maximum number of messages
     * @return The builder instance for chaining
     */
    fun limit(limit: Int): HistoryOptionsBuilder = apply {
        this.limit = limit
    }
    
    /**
     * Sets the start time.
     * @param start The start time
     * @return The builder instance for chaining
     */
    fun start(start: Instant): HistoryOptionsBuilder = apply {
        this.start = start
    }
    
    /**
     * Sets the end time.
     * @param end The end time
     * @return The builder instance for chaining
     */
    fun end(end: Instant): HistoryOptionsBuilder = apply {
        this.end = end
    }
    
    /**
     * Sets whether to reverse the order.
     * @param reverse Whether to reverse the order
     * @return The builder instance for chaining
     */
    fun reverse(reverse: Boolean = true): HistoryOptionsBuilder = apply {
        this.reverse = reverse
    }
    
    /**
     * Builds the options.
     * @return The configured HistoryOptions instance
     */
    fun build(): HistoryOptions = HistoryOptions(
        limit = limit,
        start = start,
        end = end,
        reverse = reverse
    )
}

// DSL functions for more idiomatic Kotlin usage

/**
 * Creates SubscribeOptions using a builder DSL.
 * @param block The configuration block
 * @return The configured SubscribeOptions instance
 */
inline fun subscribeOptions(block: SubscribeOptionsBuilder.() -> Unit = {}): SubscribeOptions {
    return SubscribeOptionsBuilder().apply(block).build()
}

/**
 * Creates PublishOptions using a builder DSL.
 * @param block The configuration block
 * @return The configured PublishOptions instance
 */
inline fun publishOptions(block: PublishOptionsBuilder.() -> Unit = {}): PublishOptions {
    return PublishOptionsBuilder().apply(block).build()
}

/**
 * Creates HistoryOptions using a builder DSL.
 * @param block The configuration block
 * @return The configured HistoryOptions instance
 */
inline fun historyOptions(block: HistoryOptionsBuilder.() -> Unit = {}): HistoryOptions {
    return HistoryOptionsBuilder().apply(block).build()
}
