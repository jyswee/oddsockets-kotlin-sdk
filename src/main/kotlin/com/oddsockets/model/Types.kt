package com.oddsockets.model

import com.oddsockets.exception.OddSocketsException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Represents the connection state of the OddSockets client.
 */
@Serializable
enum class ConnectionState(val value: String) {
    /** The client is disconnected. */
    DISCONNECTED("disconnected"),
    
    /** The client is connecting. */
    CONNECTING("connecting"),
    
    /** The client is connected. */
    CONNECTED("connected"),
    
    /** The client is reconnecting. */
    RECONNECTING("reconnecting"),
    
    /** The connection has failed. */
    FAILED("failed");
    
    /**
     * Whether the connection state represents a connected state.
     */
    val isConnected: Boolean
        get() = this == CONNECTED
    
    /**
     * Whether the connection state represents a connecting state.
     */
    val isConnecting: Boolean
        get() = this == CONNECTING || this == RECONNECTING
    
    /**
     * Whether the connection state represents a disconnected state.
     */
    val isDisconnected: Boolean
        get() = this == DISCONNECTED || this == FAILED
    
    override fun toString(): String = value.replaceFirstChar { it.uppercase() }
    
    companion object {
        /**
         * Creates a ConnectionState from a string value.
         * @param value The string value
         * @return The corresponding ConnectionState, or DISCONNECTED if not found
         */
        fun fromValue(value: String): ConnectionState = 
            values().find { it.value == value } ?: DISCONNECTED
    }
}

/**
 * Represents different event types emitted by the OddSockets client.
 */
@Serializable
enum class EventType(val value: String) {
    /** Emitted when the client connects. */
    CONNECTED("connected"),
    
    /** Emitted when the client disconnects. */
    DISCONNECTED("disconnected"),
    
    /** Emitted when the client reconnects. */
    RECONNECTED("reconnected"),
    
    /** Emitted when an error occurs. */
    ERROR("error"),
    
    /** Emitted when a message is received. */
    MESSAGE("message"),
    
    /** Emitted when presence information changes. */
    PRESENCE("presence"),
    
    /** Emitted when a worker is assigned. */
    WORKER_ASSIGNED("worker_assigned"),

    /** Emitted after a minted token is silently refreshed ahead of expiry. */
    TOKEN_REFRESHED("token_refreshed"),

    /** Emitted when reconnection attempts are exhausted. */
    MAX_RECONNECT_ATTEMPTS_REACHED("max_reconnect_attempts_reached");

    /**
     * Whether the event type represents a connection-related event.
     */
    val isConnectionEvent: Boolean
        get() = when (this) {
            CONNECTED, DISCONNECTED, RECONNECTED, WORKER_ASSIGNED, TOKEN_REFRESHED, MAX_RECONNECT_ATTEMPTS_REACHED -> true
            ERROR, MESSAGE, PRESENCE -> false
        }

    /**
     * Whether the event type represents a message-related event.
     */
    val isMessageEvent: Boolean
        get() = when (this) {
            MESSAGE, PRESENCE -> true
            CONNECTED, DISCONNECTED, RECONNECTED, ERROR, WORKER_ASSIGNED, TOKEN_REFRESHED, MAX_RECONNECT_ATTEMPTS_REACHED -> false
        }
    
    override fun toString(): String = value.replace("_", " ").split(" ")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    
    companion object {
        /**
         * Creates an EventType from a string value.
         * @param value The string value
         * @return The corresponding EventType, or null if not found
         */
        fun fromValue(value: String): EventType? = 
            values().find { it.value == value }
    }
}

/**
 * Common error codes used throughout the SDK.
 */
object ErrorCodes {
    /** Invalid API key format or value. */
    const val INVALID_API_KEY = "INVALID_API_KEY"
    
    /** Connection to OddSockets failed. */
    const val CONNECTION_FAILED = "CONNECTION_FAILED"
    
    /** Authentication failed. */
    const val AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED"
    
    /** Channel access denied. */
    const val CHANNEL_ACCESS_DENIED = "CHANNEL_ACCESS_DENIED"
    
    /** Message delivery failed. */
    const val MESSAGE_DELIVERY_FAILED = "MESSAGE_DELIVERY_FAILED"
    
    /** Invalid configuration. */
    const val INVALID_CONFIGURATION = "INVALID_CONFIGURATION"
    
    /** Worker assignment failed. */
    const val WORKER_ASSIGNMENT_FAILED = "WORKER_ASSIGNMENT_FAILED"
    
    /** Maximum reconnection attempts reached. */
    const val MAX_RECONNECT_ATTEMPTS_REACHED = "MAX_RECONNECT_ATTEMPTS_REACHED"
    
    /** Operation timeout. */
    const val OPERATION_TIMEOUT = "OPERATION_TIMEOUT"
    
    /** Invalid channel name. */
    const val INVALID_CHANNEL_NAME = "INVALID_CHANNEL_NAME"
}

/**
 * Type aliases for event handlers.
 */
typealias EventHandler = (Any?) -> Unit
typealias SuspendEventHandler = suspend (Any?) -> Unit
typealias MessageHandler = (Message) -> Unit
typealias SuspendMessageHandler = suspend (Message) -> Unit

/**
 * Result type for operations that can fail.
 */
sealed class OddSocketsResult<out T> {
    /** Successful result with data. */
    data class Success<T>(val data: T) : OddSocketsResult<T>()
    
    /** Failed result with error. */
    data class Failure(val error: OddSocketsException) : OddSocketsResult<Nothing>()
    
    /**
     * Returns true if this is a success result.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is a failure result.
     */
    val isFailure: Boolean get() = this is Failure
    
    /**
     * Returns the data if successful, or null if failed.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    /**
     * Returns the data if successful, or throws the error if failed.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }
    
    /**
     * Returns the data if successful, or the default value if failed.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }
    
    /**
     * Maps the success value to a new type.
     */
    inline fun <R> map(transform: (T) -> R): OddSocketsResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }
    
    /**
     * Flat maps the success value to a new result.
     */
    inline fun <R> flatMap(transform: (T) -> OddSocketsResult<R>): OddSocketsResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }
    
    /**
     * Executes the given action if this is a success.
     */
    inline fun onSuccess(action: (T) -> Unit): OddSocketsResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes the given action if this is a failure.
     */
    inline fun onFailure(action: (OddSocketsException) -> Unit): OddSocketsResult<T> {
        if (this is Failure) action(error)
        return this
    }
    
    companion object {
        /**
         * Creates a success result.
         */
        fun <T> success(data: T): OddSocketsResult<T> = Success(data)
        
        /**
         * Creates a failure result.
         */
        fun <T> failure(error: OddSocketsException): OddSocketsResult<T> = Failure(error)
        
        /**
         * Creates a failure result from a throwable.
         */
        fun <T> failure(throwable: Throwable): OddSocketsResult<T> = 
            Failure(OddSocketsException.from(throwable))
        
        /**
         * Wraps a block that might throw an exception.
         */
        inline fun <T> runCatching(block: () -> T): OddSocketsResult<T> = try {
            success(block())
        } catch (e: Exception) {
            failure(e)
        }
        
        /**
         * Wraps a suspending block that might throw an exception.
         */
        suspend inline fun <T> runSuspendCatching(crossinline block: suspend () -> T): OddSocketsResult<T> = try {
            success(block())
        } catch (e: Exception) {
            failure(e)
        }
    }
}

/**
 * A short-lived realtime token minted by the control plane, returned by a
 * [com.oddsockets.config.OddSocketsConfig.tokenProvider] callback. Game/app
 * clients present this in place of a static API key (FEAT-2026-0824-0040).
 *
 * @property token The minted realtime token (required).
 * @property expiresAt Expiry as an ISO-8601 string or an epoch (seconds or ms)
 *   rendered as a string; used to time the pre-expiry refresh.
 * @property exp Expiry as JWT epoch seconds; used when [expiresAt] is absent.
 * @property baseUrl Optional manager base URL hint from the mint response.
 * @property identity Optional resolved identity string from the mint response.
 */
@Serializable
data class OddSocketsToken(
    val token: String,
    val expiresAt: String? = null,
    val exp: Long? = null,
    val baseUrl: String? = null,
    val identity: String? = null
)

/**
 * Worker assignment response from the manager.
 */
@Serializable
internal data class WorkerAssignment(
    val url: String? = null,
    val workerId: String? = null,
    val session: String? = null
)

/**
 * Common message types for structured messaging.
 */
object MessageTypes {
    
    /**
     * A chat message structure.
     */
    @Serializable
    data class ChatMessage(
        val text: String,
        val username: String,
        val messageType: String = "chat",
        @Serializable(with = InstantSerializer::class)
        val timestamp: java.time.Instant = java.time.Instant.now()
    )
    
    /**
     * A notification message structure.
     */
    @Serializable
    data class NotificationMessage(
        val title: String,
        val body: String,
        val category: String = "general",
        val priority: String = "normal",
        @Serializable(with = InstantSerializer::class)
        val timestamp: java.time.Instant = java.time.Instant.now(),
        val data: Map<String, kotlinx.serialization.json.JsonElement>? = null
    )
    
    /**
     * A system message structure.
     */
    @Serializable
    data class SystemMessage(
        val event: String,
        val description: String,
        @Serializable(with = InstantSerializer::class)
        val timestamp: java.time.Instant = java.time.Instant.now(),
        val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null
    )
    
    /**
     * A data event message structure.
     */
    @Serializable
    data class DataEvent(
        val eventType: String,
        val payload: kotlinx.serialization.json.JsonElement,
        val source: String? = null,
        @Serializable(with = InstantSerializer::class)
        val timestamp: java.time.Instant = java.time.Instant.now()
    )
}

/**
 * Extension functions for working with JsonElement.
 */
object JsonElementExtensions {
    
    /**
     * Converts any object to JsonElement.
     */
    inline fun <reified T> T.toJsonElement(): JsonElement {
        return Json.encodeToJsonElement(this)
    }

    /**
     * Converts JsonElement to a specific type.
     */
    inline fun <reified T> JsonElement.fromJsonElement(): T? = try {
        Json.decodeFromJsonElement<T>(this)
    } catch (e: Exception) {
        null
    }

    /**
     * Gets a string value from JsonElement.
     */
    fun JsonElement.asStringOrNull(): String? = try {
        Json.decodeFromJsonElement<String>(this)
    } catch (e: Exception) {
        null
    }

    /**
     * Gets an int value from JsonElement.
     */
    fun JsonElement.asIntOrNull(): Int? = try {
        Json.decodeFromJsonElement<Int>(this)
    } catch (e: Exception) {
        null
    }

    /**
     * Gets a boolean value from JsonElement.
     */
    fun JsonElement.asBooleanOrNull(): Boolean? = try {
        Json.decodeFromJsonElement<Boolean>(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * Utility functions for creating common data structures.
 */
object OddSocketsUtils {
    
    /**
     * Generates a unique message ID.
     */
    fun generateMessageId(): String = 
        "msg_${java.util.UUID.randomUUID().toString().replace("-", "").lowercase()}"
    
    /**
     * Generates a unique user ID.
     */
    fun generateUserId(): String = 
        "user_${java.util.UUID.randomUUID().toString().replace("-", "").lowercase()}"
    
    /**
     * Creates a bulk message with string data.
     */
    fun bulkMessage(
        channel: String,
        message: String,
        options: PublishOptions? = null
    ): BulkMessage = BulkMessage.text(channel, message, options)
    
    /**
     * Creates a bulk message with object data.
     */
    inline fun <reified T> bulkMessage(
        channel: String,
        message: T,
        options: PublishOptions? = null
    ): BulkMessage = BulkMessage.obj(channel, message, options)
    
    /**
     * Creates multiple bulk messages for the same channel.
     */
    fun bulkMessages(
        channel: String,
        messages: List<String>,
        options: PublishOptions? = null
    ): List<BulkMessage> = messages.map { message ->
        BulkMessage.text(channel, message, options)
    }
    
    /**
     * Creates multiple bulk messages with different data types.
     */
    @JvmName("bulkMessagesTyped")
    inline fun <reified T> bulkMessages(
        channel: String,
        messages: List<T>,
        options: PublishOptions? = null
    ): List<BulkMessage> = messages.map { message ->
        BulkMessage.obj(channel, message, options)
    }
}

/**
 * Constants used throughout the SDK.
 */
object Constants {
    /** The current version of the OddSockets Kotlin SDK. */
    const val SDK_VERSION = "0.1.0-beta.1"
    
    /** The SDK name. */
    const val SDK_NAME = "OddSockets-Kotlin-SDK"
    
    /** The user agent string for HTTP requests. */
    const val USER_AGENT = "$SDK_NAME/$SDK_VERSION"
    
    /** Default manager URL. */
    const val DEFAULT_MANAGER_URL = "https://connect.oddsockets.tyga.network"
    
    /** Default timeout in seconds. */
    const val DEFAULT_TIMEOUT_SECONDS = 10L
    
    /** Default heartbeat interval in seconds. */
    const val DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 30L
    
    /** Default reconnect attempts. */
    const val DEFAULT_RECONNECT_ATTEMPTS = 5
    
    /** Maximum message history size. */
    const val MAX_MESSAGE_HISTORY_SIZE = 100
}
