package com.oddsockets

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enhanced Features for OddSockets Kotlin SDK
 * Provides 67 new Slack-like events with Kotlin coroutines
 */
class EnhancedFeatures(private val client: OddSocketsClient) {
    private val timeout = 10000L // 10 seconds

    // MARK: - Thread Events

    suspend fun threadReply(
        channel: String,
        parentMessageId: String,
        message: String,
        userId: String,
        userName: String
    ): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val params = mapOf(
                "channel" to channel,
                "parentMessageId" to parentMessageId,
                "message" to message,
                "userId" to userId,
                "userName" to userName
            )

            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "thread_reply") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("thread_reply_success", successHandler)
            client.once("error", errorHandler)
            client.emit("thread_reply", params)

            continuation.invokeOnCancellation {
                client.off("thread_reply_success", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    suspend fun getThread(threadId: String): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "get_thread") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("thread_data", successHandler)
            client.once("error", errorHandler)
            client.emit("get_thread", mapOf("threadId" to threadId))

            continuation.invokeOnCancellation {
                client.off("thread_data", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    suspend fun subscribeThread(threadId: String, userId: String): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "subscribe_thread") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("thread_subscribed", successHandler)
                client.once("error", errorHandler)
                client.emit("subscribe_thread", mapOf("threadId" to threadId, "userId" to userId))

                continuation.invokeOnCancellation {
                    client.off("thread_subscribed", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    fun markThreadRead(threadId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("mark_thread_read", mapOf("threadId" to threadId, "userId" to userId))
    }

    fun followThread(threadId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("follow_thread", mapOf("threadId" to threadId, "userId" to userId))
    }

    fun unfollowThread(threadId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("unfollow_thread", mapOf("threadId" to threadId, "userId" to userId))
    }

    // MARK: - Reaction Events

    fun addReaction(messageId: String, channel: String, emoji: String, userId: String, userName: String) {
        if (!client.isConnected) return
        client.emit("add_reaction", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "emoji" to emoji,
            "userId" to userId,
            "userName" to userName
        ))
    }

    fun removeReaction(messageId: String, channel: String, emoji: String, userId: String) {
        if (!client.isConnected) return
        client.emit("remove_reaction", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "emoji" to emoji,
            "userId" to userId
        ))
    }

    suspend fun getReactions(messageId: String): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "get_reactions") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("message_reactions", successHandler)
            client.once("error", errorHandler)
            client.emit("get_reactions", mapOf("messageId" to messageId))

            continuation.invokeOnCancellation {
                client.off("message_reactions", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    // MARK: - Read Receipt Events

    fun markRead(messageId: String, channel: String, userId: String, userName: String) {
        if (!client.isConnected) return
        client.emit("mark_read", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "userId" to userId,
            "userName" to userName
        ))
    }

    suspend fun getUnreadCounts(userId: String, channels: List<String>): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "get_unread_counts") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("unread_counts", successHandler)
                client.once("error", errorHandler)
                client.emit("get_unread_counts", mapOf("userId" to userId, "channels" to channels))

                continuation.invokeOnCancellation {
                    client.off("unread_counts", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    fun markAllRead(channel: String, userId: String) {
        if (!client.isConnected) return
        client.emit("mark_all_read", mapOf("channel" to channel, "userId" to userId))
    }

    // MARK: - Channel Events

    suspend fun createChannel(
        name: String,
        type: String,
        description: String,
        topic: String,
        createdBy: String,
        createdByName: String
    ): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val params = mapOf(
                "name" to name,
                "type" to type,
                "description" to description,
                "topic" to topic,
                "createdBy" to createdBy,
                "createdByName" to createdByName,
                "members" to emptyList<Any>()
            )

            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "create_channel") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("channel_create_success", successHandler)
            client.once("error", errorHandler)
            client.emit("create_channel", params)

            continuation.invokeOnCancellation {
                client.off("channel_create_success", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    fun updateChannel(channelId: String, updates: Map<String, Any>, userId: String) {
        if (!client.isConnected) return
        client.emit("update_channel", mapOf(
            "channelId" to channelId,
            "updates" to updates,
            "userId" to userId
        ))
    }

    fun archiveChannel(channelId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("archive_channel", mapOf("channelId" to channelId, "userId" to userId))
    }

    fun inviteToChannel(channelId: String, invitedUserId: String, invitedUserName: String, invitedBy: String) {
        if (!client.isConnected) return
        client.emit("invite_to_channel", mapOf(
            "channelId" to channelId,
            "invitedUserId" to invitedUserId,
            "invitedUserName" to invitedUserName,
            "invitedBy" to invitedBy
        ))
    }

    fun removeFromChannel(channelId: String, removedUserId: String, removedBy: String) {
        if (!client.isConnected) return
        client.emit("remove_from_channel", mapOf(
            "channelId" to channelId,
            "removedUserId" to removedUserId,
            "removedBy" to removedBy
        ))
    }

    fun joinChannel(channelId: String, userId: String, userName: String) {
        if (!client.isConnected) return
        client.emit("join_channel", mapOf(
            "channelId" to channelId,
            "userId" to userId,
            "userName" to userName
        ))
    }

    fun leaveChannel(channelId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("leave_channel", mapOf("channelId" to channelId, "userId" to userId))
    }

    suspend fun getChannelMembers(channelId: String): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "get_channel_members") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("channel_members", successHandler)
            client.once("error", errorHandler)
            client.emit("get_channel_members", mapOf("channelId" to channelId))

            continuation.invokeOnCancellation {
                client.off("channel_members", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    // MARK: - Direct Message Events

    suspend fun createDM(userIds: List<String>, type: String): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val params = mapOf("userIds" to userIds, "type" to type)

            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "create_dm") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("dm_create_success", successHandler)
            client.once("error", errorHandler)
            client.emit("create_dm", params)

            continuation.invokeOnCancellation {
                client.off("dm_create_success", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    fun sendDM(conversationId: String, message: String, userId: String, userName: String) {
        if (!client.isConnected) return
        client.emit("send_dm", mapOf(
            "conversationId" to conversationId,
            "message" to message,
            "userId" to userId,
            "userName" to userName
        ))
    }

    suspend fun getDMConversations(userId: String, includeArchived: Boolean): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "get_dm_conversations") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("dm_conversations", successHandler)
                client.once("error", errorHandler)
                client.emit("get_dm_conversations", mapOf(
                    "userId" to userId,
                    "includeArchived" to includeArchived
                ))

                continuation.invokeOnCancellation {
                    client.off("dm_conversations", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    // MARK: - Notification Events

    fun subscribeNotifications(userId: String) {
        if (!client.isConnected) return
        client.emit("subscribe_notifications", mapOf("userId" to userId))
    }

    fun markNotificationRead(notificationId: String, userId: String) {
        if (!client.isConnected) return
        client.emit("mark_notification_read", mapOf(
            "notificationId" to notificationId,
            "userId" to userId
        ))
    }

    fun markAllNotificationsRead(userId: String) {
        if (!client.isConnected) return
        client.emit("mark_all_notifications_read", mapOf("userId" to userId))
    }

    fun clearNotifications(userId: String) {
        if (!client.isConnected) return
        client.emit("clear_notifications", mapOf("userId" to userId))
    }

    suspend fun getNotifications(userId: String, limit: Int, status: String? = null): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit)
                status?.let { params["status"] = it }

                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "get_notifications") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("notifications_data", successHandler)
                client.once("error", errorHandler)
                client.emit("get_notifications", params)

                continuation.invokeOnCancellation {
                    client.off("notifications_data", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    // MARK: - Presence Events

    fun setStatus(userId: String, status: String) {
        if (!client.isConnected) return
        client.emit("set_status", mapOf("userId" to userId, "status" to status))
    }

    fun setCustomStatus(userId: String, emoji: String, text: String, expiresAt: String? = null) {
        if (!client.isConnected) return
        val params = mutableMapOf<String, Any>("userId" to userId, "emoji" to emoji, "text" to text)
        expiresAt?.let { params["expiresAt"] = it }
        client.emit("set_custom_status", params)
    }

    fun clearCustomStatus(userId: String) {
        if (!client.isConnected) return
        client.emit("clear_custom_status", mapOf("userId" to userId))
    }

    fun setDND(userId: String, until: String? = null) {
        if (!client.isConnected) return
        val params = mutableMapOf<String, Any>("userId" to userId)
        until?.let { params["until"] = it }
        client.emit("set_dnd", params)
    }

    fun clearDND(userId: String) {
        if (!client.isConnected) return
        client.emit("clear_dnd", mapOf("userId" to userId))
    }

    fun startTyping(userId: String, channel: String) {
        if (!client.isConnected) return
        client.emit("start_typing", mapOf("userId" to userId, "channel" to channel))
    }

    fun stopTyping(userId: String, channel: String) {
        if (!client.isConnected) return
        client.emit("stop_typing", mapOf("userId" to userId, "channel" to channel))
    }

    suspend fun getUserPresence(userIds: List<String>): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "get_user_presence") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("user_presence_data", successHandler)
            client.once("error", errorHandler)
            client.emit("get_user_presence", mapOf("userIds" to userIds))

            continuation.invokeOnCancellation {
                client.off("user_presence_data", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    // MARK: - Message Editing Events

    fun editMessage(messageId: String, channel: String, newContent: String, userId: String) {
        if (!client.isConnected) return
        client.emit("edit_message", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "newContent" to newContent,
            "userId" to userId
        ))
    }

    fun deleteMessage(messageId: String, channel: String, userId: String) {
        if (!client.isConnected) return
        client.emit("delete_message", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "userId" to userId
        ))
    }

    fun pinMessage(messageId: String, channel: String, userId: String) {
        if (!client.isConnected) return
        client.emit("pin_message", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "userId" to userId
        ))
    }

    fun unpinMessage(messageId: String, channel: String, userId: String) {
        if (!client.isConnected) return
        client.emit("unpin_message", mapOf(
            "messageId" to messageId,
            "channel" to channel,
            "userId" to userId
        ))
    }

    suspend fun getPinnedMessages(channel: String): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "get_pinned_messages") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("pinned_messages", successHandler)
            client.once("error", errorHandler)
            client.emit("get_pinned_messages", mapOf("channel" to channel))

            continuation.invokeOnCancellation {
                client.off("pinned_messages", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    // MARK: - Search Events

    suspend fun searchMessages(query: String, userId: String, limit: Int): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val params = mapOf("query" to query, "userId" to userId, "limit" to limit)

                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "search_messages") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("search_results", successHandler)
                client.once("error", errorHandler)
                client.emit("search_messages", params)

                continuation.invokeOnCancellation {
                    client.off("search_results", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    suspend fun filterMessages(filters: Map<String, Any>): Map<String, Any> = withTimeout(timeout) {
        if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

        suspendCancellableCoroutine { continuation ->
            val successHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as Map<String, Any>)
                }
            }

            val errorHandler: (Any) -> Unit = { data ->
                (data as? Map<*, *>)?.let { error ->
                    if (error["event"] == "filter_messages") {
                        continuation.resumeWithException(
                            OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                        )
                    }
                }
            }

            client.once("filter_results", successHandler)
            client.once("error", errorHandler)
            client.emit("filter_messages", filters)

            continuation.invokeOnCancellation {
                client.off("filter_results", successHandler)
                client.off("error", errorHandler)
            }
        }
    }

    suspend fun searchInChannel(channel: String, query: String, limit: Int): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val params = mapOf("channel" to channel, "query" to query, "limit" to limit)

                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "search_in_channel") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("channel_search_results", successHandler)
                client.once("error", errorHandler)
                client.emit("search_in_channel", params)

                continuation.invokeOnCancellation {
                    client.off("channel_search_results", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }

    suspend fun searchByUser(userId: String, query: String? = null, limit: Int): Map<String, Any> = 
        withTimeout(timeout) {
            if (!client.isConnected) throw OddSocketsException("Not connected to OddSockets")

            suspendCancellableCoroutine { continuation ->
                val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit)
                query?.let { params["query"] = it }

                val successHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(it as Map<String, Any>)
                    }
                }

                val errorHandler: (Any) -> Unit = { data ->
                    (data as? Map<*, *>)?.let { error ->
                        if (error["event"] == "search_by_user") {
                            continuation.resumeWithException(
                                OddSocketsException(error["message"]?.toString() ?: "Unknown error")
                            )
                        }
                    }
                }

                client.once("user_search_results", successHandler)
                client.once("error", errorHandler)
                client.emit("search_by_user", params)

                continuation.invokeOnCancellation {
                    client.off("user_search_results", successHandler)
                    client.off("error", errorHandler)
                }
            }
        }
}
