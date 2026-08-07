# OddSockets Kotlin SDK

Official Kotlin SDK for OddSockets real-time messaging platform. Pub/sub, presence, message history. Coroutines-first, Android and JVM.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.oddsockets:oddsockets-kotlin-sdk:0.1.0-beta.1")
}
```

## Quick Start

```kotlin
val client = OddSocketsClient(OddSocketsConfig(apiKey = "YOUR_API_KEY", userId = "my-agent"))
client.connect()

val channel = client.channel("my-channel")
channel.subscribe { msg -> println("Received: $msg") }
channel.publish(mapOf("text" to "Hello from Kotlin"))
```

## Manager URL

The manager URL is resolved in this order:

1. `managerUrl` on `OddSocketsConfig` / `OddSocketsConfigBuilder`
2. the `ODDSOCKETS_MANAGER_URL` environment variable
3. `https://connect.oddsockets.tyga.network`

It must be an absolute `http://` or `https://` URL, otherwise an `IllegalArgumentException`
is thrown with the message `Invalid managerUrl: <value>`. Point it at a self-hosted or
staging manager and the SDK will use that endpoint and nothing else: if it is unreachable
the connection fails with the underlying error rather than falling back to the public
endpoint.

```kotlin
val client = OddSocketsClient(
    OddSocketsConfig(apiKey = "YOUR_API_KEY", managerUrl = "https://manager.internal.example.com")
)
```

## Enhanced Features

Beyond core pub/sub, OddSockets ships a Slack-like **enhanced surface** — reactions,
typing indicators, threads, read receipts, presence/status, notifications, DMs,
channel management, message editing and search. It lives on `client.enhanced`.
The pattern is always the same:

1. **Send** an action with a `client.enhanced.*` method (camelCase, positional
   arguments).
2. **Receive** the paired broadcast with `client.on("<event>") { data -> ... }` — the
   worker forwards every enhanced broadcast onto the client's raw event surface
   (delivered as a `kotlinx.serialization.json.JsonElement?`).

```kotlin
import com.oddsockets.OddSocketsClient
import com.oddsockets.config.OddSocketsConfig

val client = OddSocketsClient(OddSocketsConfig(apiKey = "YOUR_API_KEY", userId = "alice"))
client.connect()

val channel = client.channel("room-42")
channel.subscribe { msg -> println("Received: $msg") }

// Receive-path: broadcasts from other users on the channel
client.on("user_typing")    { data -> println("someone is typing: $data") }
client.on("reaction_added") { data -> println("reaction added: $data") }
client.on("thread_reply")   { data -> println("new thread reply: $data") }

// Send-path: enhanced actions over the live socket
client.enhanced.startTyping("alice", "room-42")
client.enhanced.addReaction("msg-1", "room-42", ":thumbsup:", "alice", "Alice")

// suspend query/action methods return a JsonObject with the worker ack
val reply = client.enhanced.threadReply("room-42", "msg-1", "Replying in the thread", "alice", "Alice")
```

Each area exposes methods on `client.enhanced`; the worker broadcasts the paired
events which you handle with `client.on(...)`. Query methods (`get*`, `search*`) and
the request-style actions are `suspend` functions that return a `JsonObject` with the
worker response.

| Area | Requests (`client.enhanced.*`) | Broadcast events (`client.on`) |
|------|--------------------------------|--------------------------------|
| Typing | `startTyping`, `stopTyping` | `user_typing`, `user_stopped_typing` |
| Reactions | `addReaction`, `removeReaction`, `getReactions` | `reaction_added`, `reaction_removed` |
| Threads | `threadReply`, `getThread`, `subscribeThread`, `followThread`, `unfollowThread`, `markThreadRead` | `thread_reply`, `thread_subscribed`, `thread_followed`, `thread_read_updated` |
| Read receipts | `markRead`, `markAllRead`, `getUnreadCounts` | `user_read`, `unread_count_updated`, `all_marked_read` |
| Messages | `editMessage`, `deleteMessage`, `pinMessage`, `unpinMessage`, `getPinnedMessages` | `message_edited`, `message_deleted`, `message_pinned`, `message_unpinned` |
| Presence & status | `setStatus`, `setCustomStatus`, `clearCustomStatus`, `setDND`, `clearDND`, `getUserPresence` | `user_status_changed`, `custom_status_updated`, `dnd_status_changed` |
| Channels | `createChannel`, `updateChannel`, `archiveChannel`, `inviteToChannel`, `joinChannel`, `leaveChannel`, `getChannelMembers` | `channel_created`, `channel_updated`, `user_invited`, `user_joined_channel`, `user_left_channel` |
| DMs | `createDM`, `sendDM`, `getDMConversations` | `dm_created`, `dm_received` |
| Notifications | `subscribeNotifications`, `getNotifications`, `markNotificationRead`, `clearNotifications` | `notification`, `notification_read`, `notifications_cleared` |
| Search | `searchMessages`, `searchInChannel`, `searchByUser`, `filterMessages` | (query results returned as `JsonObject`) |

For any worker event not wrapped above, subscribe with the raw
`client.on("<event>") { ... }` API — all enhanced broadcasts are forwarded onto the
client surface.

## Get a Free API Key

```bash
curl -X POST https://oddsockets.com/api/agent-signup \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "agentName": "my-agent", "platform": "kotlin"}'
# Verify with 6-digit code:
curl -X POST https://oddsockets.com/api/agent-signup/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "code": "123456", "agentName": "my-agent"}'
```

## Plans

| | Free | Starter | Pro |
|---|---|---|---|
| **Price** | $0/mo | $49.99/mo | $299/mo |
| **MAU** | 100 | 1,000 | 50,000 |
| **Concurrent connections** | 50 | 1,000 | Unlimited |
| **Messages/day** | 10,000 | 4,320,000 | Unlimited |
| **Channels** | 10 | Unlimited | Unlimited |
| **Storage** | 100MB (24h) | 50GB (6 months) | Unlimited |

All limits are enforced in real time.

## Support

- [Documentation](https://docs.oddsockets.com/sdks/kotlin)
- [Issue Tracker](https://github.com/jyswee/oddsockets-kotlin-sdk/issues)
- [Email Support](mailto:support@oddsockets.com)

## License

MIT License - Copyright (c) 2026 Joe Wee, Tyga.Cloud Ltd. See [LICENSE](LICENSE) for details.
