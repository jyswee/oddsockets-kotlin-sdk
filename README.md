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

## Token auth for game clients (`tokenProvider`)

Game and app clients should never ship a static API key. Instead, mint a
short-lived realtime token from your own backend and hand it to the SDK through a
`tokenProvider` callback. The client resolves a **fresh** token before every
(re)connect, presents it on the manager/worker handshake in place of an API key,
and silently refreshes it ahead of expiry.

The callback is a `suspend` function returning an `OddSocketsToken`, so it can do
its own async HTTP. No `apiKey` is required when a `tokenProvider` is set.

```kotlin
val config = OddSocketsConfig.builderWithTokenProvider {
    // Your backend exchanges the player's session for a realtime token.
    val minted = myBackend.mintRealtimeToken() // suspend HTTP call
    OddSocketsToken(
        token = minted.token,
        expiresAt = minted.expiresAt // ISO-8601 or epoch; used to time the refresh
    )
}
    .userId("player-42")
    .build()

val client = OddSocketsClient(config)
client.connect()

// Fired after each silent pre-expiry refresh.
client.on(EventType.TOKEN_REFRESHED) { info ->
    // info = mapOf("expiresAt" to <epoch ms of the new token>)
}
```

Tune how early the token refreshes with `.tokenRefreshLeadMs(120_000)` on the
builder (default two minutes).

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

## Get an API Key

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

No free tier — every plan starts with a 7-day free trial.

| | Starter | Pro | Scale | Enterprise |
|---|---|---|---|---|
| **Price** | $29/mo | $99/mo | $299/mo | Contact sales |
| **Messages/mo** | 5M | 25M | 100M | Unlimited |
| **Peak connections** | 200 | 1,000 | 5,000 | Unlimited |
| **MAU** | Unlimited | Unlimited | Unlimited | Unlimited |
| **Extra messages** | $2.50/M | $1.60/M | $1.00/M | Included |

Current pricing: [oddsockets.com/#pricing](https://oddsockets.com/#pricing).

All limits are enforced in real time.

## Get Accredited

<a href="https://tyga.games/accreditation"><img src="https://prodmedia.tyga.host/public/tyga.cloud/landing/tyga.games/tygagames-black-words.svg" alt="tyga.games accreditation" height="44"></a>

Prove you can build and operate real-time features on OddSockets — channels, presence, pub/sub, delivery guarantees and production liveops — on the stack itself. Three tiers (**TCU / TCA / TCP**), certified through **tyga.games** and delivered on ClassaaS.

[**Get accredited on tyga.games →**](https://tyga.games/accreditation)

## Support

- [Documentation](https://docs.oddsockets.com/sdks/kotlin)
- [Issue Tracker](https://github.com/jyswee/oddsockets-kotlin-sdk/issues)
- [Email Support](mailto:support@oddsockets.com)

## License

MIT License - Copyright (c) 2026 Joe Wee, Tyga.Cloud Ltd. See [LICENSE](LICENSE) for details.
