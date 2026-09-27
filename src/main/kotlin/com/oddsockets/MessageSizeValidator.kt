package com.oddsockets

import com.oddsockets.exception.MessageException

/**
 * Platform message size limit, enforced server-side
 */
object MessageSizeLimits {
    const val MAX_MESSAGE_SIZE = 32768 // 32KB in bytes
    const val MAX_MESSAGE_SIZE_KB = 32
}

/**
 * Message size validator
 * 
 * Validates message sizes against the platform limit to ensure reliable
 * real-time messaging performance.
 */
object MessageSizeValidator {
    
    /**
     * Validate message size
     * @param message Message to validate
     * @throws MessageException If message exceeds size limit
     * @return Message size in bytes
     */
    fun validateMessageSize(message: Any?): Int {
        val messageStr = when (message) {
            is String -> message
            else -> kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                kotlinx.serialization.json.Json.parseToJsonElement(message.toString())
            )
        }
        
        val messageSize = messageStr.toByteArray(Charsets.UTF_8).size
        
        if (messageSize > MessageSizeLimits.MAX_MESSAGE_SIZE) {
            throw MessageException(
                "Message size (${messageSize / 1024}KB) exceeds maximum allowed size of ${MessageSizeLimits.MAX_MESSAGE_SIZE_KB}KB. " +
                "Split the payload, or publish a reference to it instead."
            )
        }
        
        return messageSize
    }
}
