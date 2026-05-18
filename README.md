# OddSockets Kotlin SDK

Official Kotlin SDK for OddSockets real-time messaging platform. Pub/sub, presence, message history. Coroutines-first, Android and JVM.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.oddsockets:oddsockets-kotlin-sdk:1.0.0")
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
