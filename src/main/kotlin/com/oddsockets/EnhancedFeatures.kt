package com.oddsockets

import com.oddsockets.exception.ConnectionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/**
 * Enhanced (Slack-like) event surface for the OddSockets Kotlin SDK.
 *
 * Every method here is wired to the REAL Socket.IO transport: fire-and-forget
 * actions are sent with [OddSocketsClient.emit]; request/response actions emit an
 * event and await the correlated worker success event via [OddSocketsClient.once].
 * Broadcast events (e.g. "user_typing", "reaction_added") are delivered to any
 * listener registered with `client.on("<event>") { ... }`.
 */
class EnhancedFeatures internal constructor(private val client: OddSocketsClient) {

    // ==================== THREAD EVENTS ====================

    /** Reply to a message in a thread. */
    suspend fun threadReply(
        channel: String,
        parentMessageId: String,
        message: String,
        userId: String,
        userName: String
    ): JsonObject = request("thread_reply", "thread_reply_success", buildJsonObject {
        put("channel", channel)
        put("parentMessageId", parentMessageId)
        put("message", message)
        put("userId", userId)
        put("userName", userName)
    })

    /** Get a thread with all replies. */
    suspend fun getThread(threadId: String): JsonObject =
        request("get_thread", "thread_data", buildJsonObject { put("threadId", threadId) })

    /** Subscribe to thread updates. */
    suspend fun subscribeThread(threadId: String, userId: String): JsonObject =
        request("subscribe_thread", "thread_subscribed", buildJsonObject {
            put("threadId", threadId)
            put("userId", userId)
        })

    /** Mark a thread as read. */
    fun markThreadRead(threadId: String, userId: String) = emit("mark_thread_read", buildJsonObject {
        put("threadId", threadId)
        put("userId", userId)
    })

    /** Follow a thread. */
    fun followThread(threadId: String, userId: String) = emit("follow_thread", buildJsonObject {
        put("threadId", threadId)
        put("userId", userId)
    })

    /** Unfollow a thread. */
    fun unfollowThread(threadId: String, userId: String) = emit("unfollow_thread", buildJsonObject {
        put("threadId", threadId)
        put("userId", userId)
    })

    // ==================== REACTION EVENTS ====================

    /** Add a reaction to a message. */
    fun addReaction(messageId: String, channel: String, emoji: String, userId: String, userName: String) =
        emit("add_reaction", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("emoji", emoji)
            put("userId", userId)
            put("userName", userName)
        })

    /** Remove a reaction from a message. */
    fun removeReaction(messageId: String, channel: String, emoji: String, userId: String) =
        emit("remove_reaction", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("emoji", emoji)
            put("userId", userId)
        })

    /** Get all reactions for a message. */
    suspend fun getReactions(messageId: String): JsonObject =
        request("get_reactions", "message_reactions", buildJsonObject { put("messageId", messageId) })

    // ==================== READ RECEIPT EVENTS ====================

    /** Mark a message as read. */
    fun markRead(messageId: String, channel: String, userId: String, userName: String) =
        emit("mark_read", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("userId", userId)
            put("userName", userName)
        })

    /** Get unread counts for channels. */
    suspend fun getUnreadCounts(userId: String, channels: List<String>): JsonObject =
        request("get_unread_counts", "unread_counts", buildJsonObject {
            put("userId", userId)
            put("channels", buildJsonArray { channels.forEach { add(it) } })
        })

    /** Mark all messages in a channel as read. */
    fun markAllRead(channel: String, userId: String) = emit("mark_all_read", buildJsonObject {
        put("channel", channel)
        put("userId", userId)
    })

    // ==================== CHANNEL EVENTS ====================

    /** Create a new channel. */
    suspend fun createChannel(
        name: String,
        type: String,
        description: String,
        topic: String,
        createdBy: String,
        createdByName: String
    ): JsonObject = request("create_channel", "channel_create_success", buildJsonObject {
        put("name", name)
        put("type", type)
        put("description", description)
        put("topic", topic)
        put("createdBy", createdBy)
        put("createdByName", createdByName)
        put("members", buildJsonArray { })
    })

    /** Update channel details. */
    fun updateChannel(channelId: String, updates: JsonObject, userId: String) =
        emit("update_channel", buildJsonObject {
            put("channelId", channelId)
            put("updates", updates)
            put("userId", userId)
        })

    /** Archive a channel. */
    fun archiveChannel(channelId: String, userId: String) = emit("archive_channel", buildJsonObject {
        put("channelId", channelId)
        put("userId", userId)
    })

    /** Invite a user to a channel. */
    fun inviteToChannel(channelId: String, invitedUserId: String, invitedUserName: String, invitedBy: String) =
        emit("invite_to_channel", buildJsonObject {
            put("channelId", channelId)
            put("invitedUserId", invitedUserId)
            put("invitedUserName", invitedUserName)
            put("invitedBy", invitedBy)
        })

    /** Remove a user from a channel. */
    fun removeFromChannel(channelId: String, removedUserId: String, removedBy: String) =
        emit("remove_from_channel", buildJsonObject {
            put("channelId", channelId)
            put("removedUserId", removedUserId)
            put("removedBy", removedBy)
        })

    /** Join a public channel. */
    fun joinChannel(channelId: String, userId: String, userName: String) =
        emit("join_channel", buildJsonObject {
            put("channelId", channelId)
            put("userId", userId)
            put("userName", userName)
        })

    /** Leave a channel. */
    fun leaveChannel(channelId: String, userId: String) = emit("leave_channel", buildJsonObject {
        put("channelId", channelId)
        put("userId", userId)
    })

    /** Get channel members. */
    suspend fun getChannelMembers(channelId: String): JsonObject =
        request("get_channel_members", "channel_members", buildJsonObject { put("channelId", channelId) })

    // ==================== DIRECT MESSAGE EVENTS ====================

    /** Create or get a DM conversation. */
    suspend fun createDM(userIds: List<String>, type: String): JsonObject =
        request("create_dm", "dm_create_success", buildJsonObject {
            put("userIds", buildJsonArray { userIds.forEach { add(it) } })
            put("type", type)
        })

    /** Send a direct message. */
    fun sendDM(conversationId: String, message: String, userId: String, userName: String) =
        emit("send_dm", buildJsonObject {
            put("conversationId", conversationId)
            put("message", message)
            put("userId", userId)
            put("userName", userName)
        })

    /** Get a user's DM conversations. */
    suspend fun getDMConversations(userId: String, includeArchived: Boolean): JsonObject =
        request("get_dm_conversations", "dm_conversations", buildJsonObject {
            put("userId", userId)
            put("includeArchived", includeArchived)
        })

    // ==================== NOTIFICATION EVENTS ====================

    /** Subscribe to user notifications. */
    fun subscribeNotifications(userId: String) =
        emit("subscribe_notifications", buildJsonObject { put("userId", userId) })

    /** Mark a notification as read. */
    fun markNotificationRead(notificationId: String, userId: String) =
        emit("mark_notification_read", buildJsonObject {
            put("notificationId", notificationId)
            put("userId", userId)
        })

    /** Mark all notifications as read. */
    fun markAllNotificationsRead(userId: String) =
        emit("mark_all_notifications_read", buildJsonObject { put("userId", userId) })

    /** Clear all notifications. */
    fun clearNotifications(userId: String) =
        emit("clear_notifications", buildJsonObject { put("userId", userId) })

    /** Get user notifications. */
    suspend fun getNotifications(userId: String, limit: Int, status: String): JsonObject =
        request("get_notifications", "notifications_data", buildJsonObject {
            put("userId", userId)
            put("limit", limit)
            put("status", status)
        })

    // ==================== PRESENCE EVENTS ====================

    /** Set user status. */
    fun setStatus(userId: String, status: String) = emit("set_status", buildJsonObject {
        put("userId", userId)
        put("status", status)
    })

    /** Set a custom status. */
    fun setCustomStatus(userId: String, emoji: String, text: String, expiresAt: String?) =
        emit("set_custom_status", buildJsonObject {
            put("userId", userId)
            put("emoji", emoji)
            put("text", text)
            if (expiresAt != null) put("expiresAt", expiresAt)
        })

    /** Clear a custom status. */
    fun clearCustomStatus(userId: String) =
        emit("clear_custom_status", buildJsonObject { put("userId", userId) })

    /** Enable Do Not Disturb. */
    fun setDND(userId: String, until: String?) = emit("set_dnd", buildJsonObject {
        put("userId", userId)
        if (until != null) put("until", until)
    })

    /** Disable Do Not Disturb. */
    fun clearDND(userId: String) = emit("clear_dnd", buildJsonObject { put("userId", userId) })

    /** Start a typing indicator. */
    fun startTyping(userId: String, channel: String) = emit("start_typing", buildJsonObject {
        put("userId", userId)
        put("channel", channel)
    })

    /** Stop a typing indicator. */
    fun stopTyping(userId: String, channel: String) = emit("stop_typing", buildJsonObject {
        put("userId", userId)
        put("channel", channel)
    })

    /** Get presence information for a set of users. */
    suspend fun getUserPresence(userIds: List<String>): JsonObject =
        request("get_user_presence", "user_presence_data", buildJsonObject {
            put("userIds", buildJsonArray { userIds.forEach { add(it) } })
        })

    // ==================== MESSAGE EDITING EVENTS ====================

    /** Edit a message. */
    fun editMessage(messageId: String, channel: String, newContent: String, userId: String) =
        emit("edit_message", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("newContent", newContent)
            put("userId", userId)
        })

    /** Delete a message. */
    fun deleteMessage(messageId: String, channel: String, userId: String) =
        emit("delete_message", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("userId", userId)
        })

    /** Pin a message to a channel. */
    fun pinMessage(messageId: String, channel: String, userId: String) =
        emit("pin_message", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("userId", userId)
        })

    /** Unpin a message from a channel. */
    fun unpinMessage(messageId: String, channel: String, userId: String) =
        emit("unpin_message", buildJsonObject {
            put("messageId", messageId)
            put("channel", channel)
            put("userId", userId)
        })

    /** Get pinned messages in a channel. */
    suspend fun getPinnedMessages(channel: String): JsonObject =
        request("get_pinned_messages", "pinned_messages", buildJsonObject { put("channel", channel) })

    // ==================== SEARCH EVENTS ====================

    /** Search messages across all channels. */
    suspend fun searchMessages(query: String, userId: String, limit: Int): JsonObject =
        request("search_messages", "search_results", buildJsonObject {
            put("query", query)
            put("userId", userId)
            put("limit", limit)
        })

    /** Filter messages by criteria. */
    suspend fun filterMessages(filters: JsonObject): JsonObject =
        request("filter_messages", "filter_results", filters)

    /** Search within a specific channel. */
    suspend fun searchInChannel(channel: String, query: String, limit: Int): JsonObject =
        request("search_in_channel", "channel_search_results", buildJsonObject {
            put("channel", channel)
            put("query", query)
            put("limit", limit)
        })

    /** Search messages by user. */
    suspend fun searchByUser(userId: String, query: String?, limit: Int): JsonObject =
        request("search_by_user", "user_search_results", buildJsonObject {
            put("userId", userId)
            if (query != null) put("query", query)
            put("limit", limit)
        })

    // ==================== INTERNAL HELPERS ====================

    private fun emit(event: String, params: JsonObject) {
        check(client.isConnected) { "Not connected to OddSockets" }
        client.emit(event, params)
    }

    /**
     * Emit [emitEvent] and await the correlated worker [successEvent]. A worker "error"
     * event naming [emitEvent] rejects the wait; a timeout fails it after 10 seconds.
     */
    private suspend fun request(emitEvent: String, successEvent: String, params: JsonObject): JsonObject {
        if (!client.isConnected) throw ConnectionException("Not connected to OddSockets")

        val deferred = CompletableDeferred<JsonObject>()

        client.once(successEvent) { data ->
            deferred.complete(data as? JsonObject ?: JsonObject(emptyMap()))
        }
        client.once("error") { data ->
            val error = data as? JsonObject
            if (error?.get("event")?.jsonPrimitive?.contentOrNull == emitEvent) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Enhanced request failed"
                deferred.completeExceptionally(RuntimeException(message))
            }
        }

        client.emit(emitEvent, params)

        return try {
            withTimeout(TIMEOUT_MILLIS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw ConnectionException("Timed out waiting for $successEvent")
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
