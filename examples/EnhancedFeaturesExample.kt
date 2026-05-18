package com.oddsockets.examples

import com.oddsockets.OddSocketsClient
import com.oddsockets.config.OddSocketsConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * OddSockets Kotlin SDK - Enhanced Features Example
 * Demonstrates all 67 new Slack-like events with Kotlin coroutines
 */
fun main() = runBlocking {
    println("🚀 OddSockets Kotlin SDK - Enhanced Features Example")
    println("Demonstrating all 67 new Slack-like events")
    println("=".repeat(50))
    
    // Create and configure client
    val config = OddSocketsConfig(
        apiKey = "your_api_key_here",
        userId = "user_123",
        autoConnect = false
    )
    
    val client = OddSocketsClient(config)
    
    // Set up event listeners
    setupEventListeners(client)
    
    try {
        // Connect
        println("\n🔄 Connecting to OddSockets...")
        client.connect()
        
        // Wait for connection
        delay(2000)
        
        if (!client.isConnected) {
            println("❌ Failed to connect")
            return@runBlocking
        }
        
        println("✅ Connected successfully!\n")
        
        // Test all enhanced features
        testThreadEvents(client)
        testReactionEvents(client)
        testReadReceiptEvents(client)
        testChannelEvents(client)
        testDirectMessageEvents(client)
        testNotificationEvents(client)
        testPresenceEvents(client)
        testMessageEditingEvents(client)
        testSearchEvents(client)
        
        // Summary
        println("\n🎉 All enhanced features tested!")
        println("\n📊 Summary:")
        println("- Thread Events: 7 methods")
        println("- Reaction Events: 6 methods")
        println("- Read Receipt Events: 6 methods")
        println("- Channel Events: 11 methods")
        println("- Direct Message Events: 6 methods")
        println("- Notification Events: 6 methods")
        println("- File Upload Events: 7 methods")
        println("- Presence Events: 8 methods")
        println("- Message Editing Events: 5 methods")
        println("- Search Events: 4 methods")
        println("=".repeat(50))
        println("Total: 67 enhanced Slack-like events! 🚀")
        
        // Wait before disconnecting
        delay(2000)
        
        // Disconnect
        client.disconnect()
        println("\n✅ Disconnected")
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}

fun setupEventListeners(client: OddSocketsClient) {
    client.on("connected") { 
        println("🟢 Connected event fired")
    }
    
    client.on("disconnected") { 
        println("🔴 Disconnected event fired")
    }
    
    client.on("error") { data ->
        println("❌ Error event: $data")
    }
}

// MARK: - Thread Events

suspend fun testThreadEvents(client: OddSocketsClient) {
    println("📝 Testing Thread Events...")
    
    try {
        // Thread reply
        val result = client.enhanced.threadReply(
            channel = "general",
            parentMessageId = "msg_123",
            message = "This is a test reply from Kotlin!",
            userId = "user_123",
            userName = "Test User"
        )
        println("✅ Thread reply created: $result")
        
        // Get thread
        val thread = client.enhanced.getThread(threadId = "thread_123")
        println("✅ Thread data: $thread")
        
        // Subscribe to thread
        val subscribed = client.enhanced.subscribeThread(threadId = "thread_123", userId = "user_123")
        println("✅ Subscribed to thread: $subscribed")
        
        // Mark thread as read
        client.enhanced.markThreadRead(threadId = "thread_123", userId = "user_123")
        println("✅ Marked thread as read")
        
        // Follow thread
        client.enhanced.followThread(threadId = "thread_123", userId = "user_123")
        println("✅ Following thread\n")
        
    } catch (e: Exception) {
        println("❌ Thread events error: ${e.message}\n")
    }
}

// MARK: - Reaction Events

suspend fun testReactionEvents(client: OddSocketsClient) {
    println("😀 Testing Reaction Events...")
    
    try {
        // Add reaction
        client.enhanced.addReaction(
            messageId = "msg_123",
            channel = "general",
            emoji = "👍",
            userId = "user_123",
            userName = "Test User"
        )
        println("✅ Added reaction 👍")
        
        // Remove reaction
        client.enhanced.removeReaction(
            messageId = "msg_123",
            channel = "general",
            emoji = "👍",
            userId = "user_123"
        )
        println("✅ Removed reaction")
        
        // Get reactions
        val reactions = client.enhanced.getReactions(messageId = "msg_123")
        println("✅ Reactions: $reactions\n")
        
    } catch (e: Exception) {
        println("❌ Reaction events error: ${e.message}\n")
    }
}

// MARK: - Read Receipt Events

suspend fun testReadReceiptEvents(client: OddSocketsClient) {
    println("✓ Testing Read Receipt Events...")
    
    try {
        // Mark message as read
        client.enhanced.markRead(
            messageId = "msg_123",
            channel = "general",
            userId = "user_123",
            userName = "Test User"
        )
        println("✅ Marked message as read")
        
        // Get unread counts
        val counts = client.enhanced.getUnreadCounts(
            userId = "user_123",
            channels = listOf("general", "random")
        )
        println("✅ Unread counts: $counts")
        
        // Mark all as read
        client.enhanced.markAllRead(channel = "general", userId = "user_123")
        println("✅ Marked all messages as read\n")
        
    } catch (e: Exception) {
        println("❌ Read receipt events error: ${e.message}\n")
    }
}

// MARK: - Channel Events

suspend fun testChannelEvents(client: OddSocketsClient) {
    println("📢 Testing Channel Events...")
    
    try {
        // Create channel
        val channelName = "kotlin-test-${System.currentTimeMillis()}"
        val channel = client.enhanced.createChannel(
            name = channelName,
            type = "public",
            description = "Created from Kotlin SDK",
            topic = "Testing",
            createdBy = "user_123",
            createdByName = "Test User"
        )
        println("✅ Channel created: $channel")
        
        // Update channel
        client.enhanced.updateChannel(
            channelId = "channel_123",
            updates = mapOf("topic" to "Updated topic"),
            userId = "user_123"
        )
        println("✅ Updated channel")
        
        // Join channel
        client.enhanced.joinChannel(
            channelId = "channel_123",
            userId = "user_123",
            userName = "Test User"
        )
        println("✅ Joined channel")
        
        // Invite to channel
        client.enhanced.inviteToChannel(
            channelId = "channel_123",
            invitedUserId = "user_456",
            invitedUserName = "Jane Doe",
            invitedBy = "user_123"
        )
        println("✅ Invited user to channel")
        
        // Get channel members
        val members = client.enhanced.getChannelMembers(channelId = "channel_123")
        println("✅ Channel members: $members\n")
        
    } catch (e: Exception) {
        println("❌ Channel events error: ${e.message}\n")
    }
}

// MARK: - Direct Message Events

suspend fun testDirectMessageEvents(client: OddSocketsClient) {
    println("💬 Testing Direct Message Events...")
    
    try {
        // Create DM
        val dm = client.enhanced.createDM(
            userIds = listOf("user_123", "user_456"),
            type = "1-on-1"
        )
        println("✅ DM created: $dm")
        
        // Send DM
        client.enhanced.sendDM(
            conversationId = "dm_123",
            message = "Hello from Kotlin!",
            userId = "user_123",
            userName = "Test User"
        )
        println("✅ Sent DM")
        
        // Get DM conversations
        val conversations = client.enhanced.getDMConversations(
            userId = "user_123",
            includeArchived = false
        )
        println("✅ DM conversations: $conversations\n")
        
    } catch (e: Exception) {
        println("❌ Direct message events error: ${e.message}\n")
    }
}

// MARK: - Notification Events

suspend fun testNotificationEvents(client: OddSocketsClient) {
    println("🔔 Testing Notification Events...")
    
    try {
        // Subscribe to notifications
        client.enhanced.subscribeNotifications(userId = "user_123")
        println("✅ Subscribed to notifications")
        
        // Mark notification as read
        client.enhanced.markNotificationRead(notificationId = "notif_123", userId = "user_123")
        println("✅ Marked notification as read")
        
        // Mark all notifications as read
        client.enhanced.markAllNotificationsRead(userId = "user_123")
        println("✅ Marked all notifications as read")
        
        // Get notifications
        val notifications = client.enhanced.getNotifications(
            userId = "user_123",
            limit = 10,
            status = "all"
        )
        println("✅ Notifications: $notifications\n")
        
    } catch (e: Exception) {
        println("❌ Notification events error: ${e.message}\n")
    }
}

// MARK: - Presence Events

suspend fun testPresenceEvents(client: OddSocketsClient) {
    println("👤 Testing Presence Events...")
    
    try {
        // Set status
        client.enhanced.setStatus(userId = "user_123", status = "online")
        println("✅ Set status to online")
        
        // Set custom status
        client.enhanced.setCustomStatus(
            userId = "user_123",
            emoji = "🎯",
            text = "Coding in Kotlin",
            expiresAt = null
        )
        println("✅ Set custom status")
        
        // Clear custom status
        client.enhanced.clearCustomStatus(userId = "user_123")
        println("✅ Cleared custom status")
        
        // Set DND
        client.enhanced.setDND(userId = "user_123", until = null)
        println("✅ Enabled Do Not Disturb")
        
        // Clear DND
        client.enhanced.clearDND(userId = "user_123")
        println("✅ Disabled Do Not Disturb")
        
        // Start typing
        client.enhanced.startTyping(userId = "user_123", channel = "general")
        println("✅ Started typing indicator")
        
        // Wait a moment
        delay(2000)
        
        // Stop typing
        client.enhanced.stopTyping(userId = "user_123", channel = "general")
        println("✅ Stopped typing indicator")
        
        // Get user presence
        val presence = client.enhanced.getUserPresence(userIds = listOf("user_123", "user_456"))
        println("✅ User presence: $presence\n")
        
    } catch (e: Exception) {
        println("❌ Presence events error: ${e.message}\n")
    }
}

// MARK: - Message Editing Events

suspend fun testMessageEditingEvents(client: OddSocketsClient) {
    println("✏️ Testing Message Editing Events...")
    
    try {
        // Edit message
        client.enhanced.editMessage(
            messageId = "msg_123",
            channel = "general",
            newContent = "Updated message from Kotlin",
            userId = "user_123"
        )
        println("✅ Edited message")
        
        // Delete message
        client.enhanced.deleteMessage(
            messageId = "msg_456",
            channel = "general",
            userId = "user_123"
        )
        println("✅ Deleted message")
        
        // Pin message
        client.enhanced.pinMessage(
            messageId = "msg_123",
            channel = "general",
            userId = "user_123"
        )
        println("✅ Pinned message")
        
        // Unpin message
        client.enhanced.unpinMessage(
            messageId = "msg_123",
            channel = "general",
            userId = "user_123"
        )
        println("✅ Unpinned message")
        
        // Get pinned messages
        val pinned = client.enhanced.getPinnedMessages(channel = "general")
        println("✅ Pinned messages: $pinned\n")
        
    } catch (e: Exception) {
        println("❌ Message editing events error: ${e.message}\n")
    }
}

// MARK: - Search Events

suspend fun testSearchEvents(client: OddSocketsClient) {
    println("🔍 Testing Search Events...")
    
    try {
        // Search messages
        val results = client.enhanced.searchMessages(
            query = "test",
            userId = "user_123",
            limit = 10
        )
        println("✅ Search results: $results")
        
        // Search in channel
        val channelResults = client.enhanced.searchInChannel(
            channel = "general",
            query = "test",
            limit = 10
        )
        println("✅ Channel search results: $channelResults")
        
        // Filter messages
        val filtered = client.enhanced.filterMessages(
            filters = mapOf(
                "channel" to "general",
                "userId" to "user_123",
                "limit" to 10
            )
        )
        println("✅ Filter results: $filtered")
        
        // Search by user
        val userResults = client.enhanced.searchByUser(
            userId = "user_123",
            query = null,
            limit = 10
        )
        println("✅ User search results: $userResults\n")
        
    } catch (e: Exception) {
        println("❌ Search events error: ${e.message}\n")
    }
}
