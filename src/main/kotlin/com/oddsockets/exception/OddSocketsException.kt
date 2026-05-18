package com.oddsockets.exception

import com.oddsockets.model.ErrorCodes

/**
 * Base exception class for all OddSockets-related errors.
 *
 * This sealed class hierarchy provides structured error handling with specific
 * exception types for different error scenarios. Each exception includes
 * detailed error information and recovery suggestions.
 *
 * @property message The error message
 * @property code The error code for programmatic handling
 * @property details Additional error details
 * @property cause The underlying cause of the error
 */
sealed class OddSocketsException(
    override val message: String,
    val code: String,
    val details: Map<String, Any>? = null,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Gets a recovery suggestion for this error.
     */
    open val recoverySuggestion: String?
        get() = when (this) {
            is InvalidConfigurationException -> "Check your configuration parameters and ensure they are valid"
            is ConnectionException -> "Check your network connection and try again"
            is AuthenticationException -> "Verify your API key is correct and active"
            is ChannelException -> "Ensure the channel name is valid and you have access permissions"
            is MessageException -> "Check the message format and try again"
            is NetworkException -> "Check your network connection and retry the operation"
            is TimeoutException -> "The operation timed out. Try increasing the timeout or retry later"
            is GenericException -> "Please check the error details and try again"
        }
    
    /**
     * Gets additional context information about the error.
     */
    val context: Map<String, Any>
        get() = buildMap {
            put("error_type", this@OddSocketsException::class.simpleName ?: "Unknown")
            put("error_code", code)
            put("timestamp", java.time.Instant.now().toString())
            details?.let { putAll(it) }
        }
    
    /**
     * Returns a detailed string representation of the error.
     */
    fun toDetailedString(): String = buildString {
        appendLine("OddSockets Error: $message")
        appendLine("Code: $code")
        appendLine("Type: ${this@OddSocketsException::class.simpleName}")
        recoverySuggestion?.let { appendLine("Suggestion: $it") }
        if (!details.isNullOrEmpty()) {
            appendLine("Details:")
            details.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
        }
        cause?.let { appendLine("Caused by: ${it.message}") }
    }
    
    companion object {
        /**
         * Creates an OddSocketsException from a generic throwable.
         * @param throwable The throwable to convert
         * @return An appropriate OddSocketsException
         */
        fun from(throwable: Throwable): OddSocketsException = when (throwable) {
            is OddSocketsException -> throwable
            is java.net.SocketTimeoutException -> TimeoutException(
                message = "Operation timed out: ${throwable.message}",
                cause = throwable
            )
            is java.net.ConnectException -> ConnectionException(
                message = "Connection failed: ${throwable.message}",
                cause = throwable
            )
            is java.net.UnknownHostException -> NetworkException(
                message = "Network error: ${throwable.message}",
                cause = throwable
            )
            is kotlinx.serialization.SerializationException -> MessageException(
                message = "Message serialization error: ${throwable.message}",
                cause = throwable
            )
            else -> GenericException(
                message = throwable.message ?: "Unknown error occurred",
                cause = throwable
            )
        }
    }
}

/**
 * Exception thrown when configuration is invalid.
 *
 * @property message The error message
 * @property details Additional configuration error details
 * @property cause The underlying cause
 */
class InvalidConfigurationException(
    message: String,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.INVALID_CONFIGURATION,
    details = details,
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for invalid API key.
         */
        fun invalidApiKey(apiKey: String): InvalidConfigurationException = 
            InvalidConfigurationException(
                message = "Invalid API key format",
                details = mapOf("api_key_prefix" to apiKey.take(10))
            )
        
        /**
         * Creates an exception for invalid URL.
         */
        fun invalidUrl(url: String): InvalidConfigurationException = 
            InvalidConfigurationException(
                message = "Invalid URL format: $url",
                details = mapOf("url" to url)
            )
        
        /**
         * Creates an exception for invalid timeout.
         */
        fun invalidTimeout(timeout: kotlin.time.Duration): InvalidConfigurationException = 
            InvalidConfigurationException(
                message = "Invalid timeout value: ${timeout.inWholeSeconds}s",
                details = mapOf("timeout_seconds" to timeout.inWholeSeconds)
            )
    }
}

/**
 * Exception thrown when connection operations fail.
 *
 * @property message The error message
 * @property details Additional connection error details
 * @property cause The underlying cause
 */
class ConnectionException(
    message: String,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.CONNECTION_FAILED,
    details = details,
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for connection timeout.
         */
        fun timeout(timeoutSeconds: Long): ConnectionException = 
            ConnectionException(
                message = "Connection timed out after ${timeoutSeconds}s",
                details = mapOf("timeout_seconds" to timeoutSeconds)
            )
        
        /**
         * Creates an exception for connection refused.
         */
        fun refused(host: String, port: Int): ConnectionException = 
            ConnectionException(
                message = "Connection refused to $host:$port",
                details = mapOf("host" to host, "port" to port)
            )
        
        /**
         * Creates an exception for worker assignment failure.
         */
        fun workerAssignmentFailed(reason: String): ConnectionException = 
            ConnectionException(
                message = "Worker assignment failed: $reason",
                details = mapOf("reason" to reason)
            )
    }
}

/**
 * Exception thrown when authentication fails.
 *
 * @property message The error message
 * @property details Additional authentication error details
 * @property cause The underlying cause
 */
class AuthenticationException(
    message: String,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.AUTHENTICATION_FAILED,
    details = details,
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for invalid API key.
         */
        fun invalidApiKey(): AuthenticationException = 
            AuthenticationException(
                message = "Invalid or expired API key",
                details = mapOf("error_type" to "invalid_api_key")
            )
        
        /**
         * Creates an exception for insufficient permissions.
         */
        fun insufficientPermissions(resource: String): AuthenticationException = 
            AuthenticationException(
                message = "Insufficient permissions to access $resource",
                details = mapOf("resource" to resource, "error_type" to "insufficient_permissions")
            )
        
        /**
         * Creates an exception for expired session.
         */
        fun sessionExpired(): AuthenticationException = 
            AuthenticationException(
                message = "Session has expired",
                details = mapOf("error_type" to "session_expired")
            )
    }
}

/**
 * Exception thrown when channel operations fail.
 *
 * @property message The error message
 * @property channelName The channel name that caused the error
 * @property details Additional channel error details
 * @property cause The underlying cause
 */
class ChannelException(
    message: String,
    val channelName: String? = null,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.CHANNEL_ACCESS_DENIED,
    details = buildMap {
        channelName?.let { put("channel", it) }
        details?.let { putAll(it) }
    },
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for invalid channel name.
         */
        fun invalidChannelName(channelName: String): ChannelException = 
            ChannelException(
                message = "Invalid channel name: $channelName",
                channelName = channelName,
                details = mapOf("error_type" to "invalid_channel_name")
            )
        
        /**
         * Creates an exception for channel access denied.
         */
        fun accessDenied(channelName: String): ChannelException = 
            ChannelException(
                message = "Access denied to channel: $channelName",
                channelName = channelName,
                details = mapOf("error_type" to "access_denied")
            )
        
        /**
         * Creates an exception for channel not found.
         */
        fun notFound(channelName: String): ChannelException = 
            ChannelException(
                message = "Channel not found: $channelName",
                channelName = channelName,
                details = mapOf("error_type" to "channel_not_found")
            )
        
        /**
         * Creates an exception for subscription failure.
         */
        fun subscriptionFailed(channelName: String, reason: String): ChannelException = 
            ChannelException(
                message = "Failed to subscribe to channel $channelName: $reason",
                channelName = channelName,
                details = mapOf("error_type" to "subscription_failed", "reason" to reason)
            )
    }
}

/**
 * Exception thrown when message operations fail.
 *
 * @property message The error message
 * @property messageId The message ID that caused the error
 * @property details Additional message error details
 * @property cause The underlying cause
 */
class MessageException(
    message: String,
    val messageId: String? = null,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.MESSAGE_DELIVERY_FAILED,
    details = buildMap {
        messageId?.let { put("message_id", it) }
        details?.let { putAll(it) }
    },
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for message delivery failure.
         */
        fun deliveryFailed(messageId: String, reason: String): MessageException = 
            MessageException(
                message = "Message delivery failed: $reason",
                messageId = messageId,
                details = mapOf("error_type" to "delivery_failed", "reason" to reason)
            )
        
        /**
         * Creates an exception for message serialization failure.
         */
        fun serializationFailed(cause: Throwable): MessageException = 
            MessageException(
                message = "Message serialization failed: ${cause.message}",
                details = mapOf("error_type" to "serialization_failed"),
                cause = cause
            )
        
        /**
         * Creates an exception for message too large.
         */
        fun tooLarge(size: Int, maxSize: Int): MessageException = 
            MessageException(
                message = "Message too large: ${size} bytes (max: ${maxSize} bytes)",
                details = mapOf(
                    "error_type" to "message_too_large",
                    "size" to size,
                    "max_size" to maxSize
                )
            )
        
        /**
         * Creates an exception for invalid message format.
         */
        fun invalidFormat(reason: String): MessageException = 
            MessageException(
                message = "Invalid message format: $reason",
                details = mapOf("error_type" to "invalid_format", "reason" to reason)
            )
    }
}

/**
 * Exception thrown when network operations fail.
 *
 * @property message The error message
 * @property details Additional network error details
 * @property cause The underlying cause
 */
class NetworkException(
    message: String,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.CONNECTION_FAILED,
    details = details,
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for DNS resolution failure.
         */
        fun dnsResolutionFailed(hostname: String): NetworkException = 
            NetworkException(
                message = "DNS resolution failed for hostname: $hostname",
                details = mapOf("hostname" to hostname, "error_type" to "dns_resolution_failed")
            )
        
        /**
         * Creates an exception for SSL/TLS errors.
         */
        fun sslError(reason: String): NetworkException = 
            NetworkException(
                message = "SSL/TLS error: $reason",
                details = mapOf("error_type" to "ssl_error", "reason" to reason)
            )
        
        /**
         * Creates an exception for proxy errors.
         */
        fun proxyError(proxyHost: String, reason: String): NetworkException = 
            NetworkException(
                message = "Proxy error via $proxyHost: $reason",
                details = mapOf("proxy_host" to proxyHost, "error_type" to "proxy_error", "reason" to reason)
            )
    }
}

/**
 * Exception thrown when operations timeout.
 *
 * @property message The error message
 * @property timeoutSeconds The timeout duration in seconds
 * @property details Additional timeout error details
 * @property cause The underlying cause
 */
class TimeoutException(
    message: String,
    val timeoutSeconds: Long? = null,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = ErrorCodes.OPERATION_TIMEOUT,
    details = buildMap {
        timeoutSeconds?.let { put("timeout_seconds", it) }
        details?.let { putAll(it) }
    },
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for connection timeout.
         */
        fun connectionTimeout(timeoutSeconds: Long): TimeoutException = 
            TimeoutException(
                message = "Connection timed out after ${timeoutSeconds}s",
                timeoutSeconds = timeoutSeconds,
                details = mapOf("operation" to "connection")
            )
        
        /**
         * Creates an exception for operation timeout.
         */
        fun operationTimeout(operation: String, timeoutSeconds: Long): TimeoutException = 
            TimeoutException(
                message = "$operation timed out after ${timeoutSeconds}s",
                timeoutSeconds = timeoutSeconds,
                details = mapOf("operation" to operation)
            )
        
        /**
         * Creates an exception for message publish timeout.
         */
        fun publishTimeout(timeoutSeconds: Long): TimeoutException = 
            TimeoutException(
                message = "Message publish timed out after ${timeoutSeconds}s",
                timeoutSeconds = timeoutSeconds,
                details = mapOf("operation" to "publish")
            )
    }
}

/**
 * Generic exception for errors that don't fit other categories.
 *
 * @property message The error message
 * @property details Additional error details
 * @property cause The underlying cause
 */
class GenericException(
    message: String,
    details: Map<String, Any>? = null,
    cause: Throwable? = null
) : OddSocketsException(
    message = message,
    code = "GENERIC_ERROR",
    details = details,
    cause = cause
) {
    companion object {
        /**
         * Creates an exception for unexpected errors.
         */
        fun unexpected(cause: Throwable): GenericException = 
            GenericException(
                message = "Unexpected error: ${cause.message ?: "Unknown error"}",
                details = mapOf("error_type" to "unexpected"),
                cause = cause
            )
        
        /**
         * Creates an exception for unsupported operations.
         */
        fun unsupportedOperation(operation: String): GenericException = 
            GenericException(
                message = "Unsupported operation: $operation",
                details = mapOf("operation" to operation, "error_type" to "unsupported_operation")
            )
        
        /**
         * Creates an exception for invalid state.
         */
        fun invalidState(currentState: String, expectedState: String): GenericException = 
            GenericException(
                message = "Invalid state: expected $expectedState, but was $currentState",
                details = mapOf(
                    "current_state" to currentState,
                    "expected_state" to expectedState,
                    "error_type" to "invalid_state"
                )
            )
    }
}

/**
 * Extension functions for exception handling.
 */

/**
 * Executes a block and catches any OddSocketsException, converting other exceptions.
 */
inline fun <T> catchOddSocketsException(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: OddSocketsException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(OddSocketsException.from(e))
}

/**
 * Executes a suspending block and catches any OddSocketsException, converting other exceptions.
 */
suspend inline fun <T> catchOddSocketsExceptionSuspend(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: OddSocketsException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(OddSocketsException.from(e))
}

/**
 * Maps a Result<T> to handle OddSocketsException specifically.
 */
inline fun <T, R> Result<T>.mapOddSocketsException(
    onSuccess: (T) -> R,
    onOddSocketsException: (OddSocketsException) -> R,
    onOtherException: (Throwable) -> R
): R = fold(
    onSuccess = onSuccess,
    onFailure = { throwable ->
        when (throwable) {
            is OddSocketsException -> onOddSocketsException(throwable)
            else -> onOtherException(throwable)
        }
    }
)
