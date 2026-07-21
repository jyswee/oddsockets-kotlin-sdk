# OddSockets Kotlin SDK - Demo

A tiny, runnable program that proves a real real-time round-trip against OddSockets
using **two independent clients**: **connect -> subscribe -> publish -> receive**.

Because the subscriber (`alice`) and the publisher (`bob`) are separate connections,
a message that reaches the subscriber can only have travelled through the OddSockets
worker - so this doubles as an honest end-to-end regression test (no mocks, no local
echo). The SDK speaks genuine Socket.IO (Engine.IO v4) over a WebSocket to the
assigned worker, exactly like the JavaScript and Python SDKs.

## Proof it's real

`demo/PROOF.txt` is a captured transcript of this demo running in Docker against the
live platform. Reproduce it yourself in one command (see below) - here is a real run:

```
[connect] connecting both clients...
[alice] worker [instance]
[bob]   worker [instance]
[connect] alice = Connected, bob = Connected
[alice] subscribed to demo-204561 (presence on)
[bob] published, messageId = c5436b63-5b59-4596-81b3-d1a4cbaaaaa0
[alice] received bob's message (nonce matched) - real round-trip.
[alice] presence: 1 user(s).
[alice] unsubscribed.

OK - cross-client round-trip verified
```

## 1. Get a free API key

Two-step email verification (no card required):

```bash
# Step 1 - request a code
curl -X POST https://oddsockets.com/api/agent-signup \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","agentName":"demo","platform":"kotlin"}'

# Step 2 - verify and receive your apiKey
curl -X POST https://oddsockets.com/api/agent-signup/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","code":"123456","agentName":"demo"}'
```

The verify response contains your `apiKey` (starts with `ak_`).

## 2. Run it in Docker (recommended)

No local JDK/Gradle toolchain needed. Build from the repo root so the SDK source is
in context (the demo uses a Gradle composite build - `includeBuild("..")` - to
compile the SDK straight from the parent, without publishing anything):

```bash
docker build -f demo/Dockerfile -t oddsockets-kotlin-demo .
docker run --rm -e ODDSOCKETS_API_KEY="ak_your_key_here" oddsockets-kotlin-demo
```

Compilation happens at image-build time, so a broken SDK fails the build - only a
genuinely-compiling SDK produces a runnable image. A successful run prints
`OK - cross-client round-trip verified` and exits `0`.

## 2b. Run it locally with Gradle

Requires a JDK 11+ toolchain. The composite build resolves the SDK from the parent
directory, so the demo is clone-and-run:

```bash
cd demo
export ODDSOCKETS_API_KEY="ak_your_key_here"
./gradlew run
```

The key is read from `ODDSOCKETS_API_KEY` and never hardcoded; if it is missing the
program prints the signup instructions above and exits non-zero.

## The code, step by step

Create two clients - a subscriber and a publisher - each on its own connection:

```kotlin
val subscriber = OddSocketsClient(
    OddSocketsConfig(apiKey = apiKey, userId = "alice", autoConnect = false)
)
val publisher = OddSocketsClient(
    OddSocketsConfig(apiKey = apiKey, userId = "bob", autoConnect = false)
)

subscriber.connect()
publisher.connect()
```

Subscribe on the subscriber (presence enabled):

```kotlin
val inbox = subscriber.channel("my-channel")
inbox.subscribe({ message ->
    println("received: ${message.data}")
}, subscribeOptions { enablePresence(true) })
```

Publish from the *other* client - this is what makes the test honest:

```kotlin
val outbox = publisher.channel("my-channel")
val ack = outbox.publish(DemoMessage("hello from bob", nonce, "bob"))
println("messageId = ${ack.messageId}")
```

Inspect presence, then tear down cleanly:

```kotlin
val presence = inbox.getPresence()
println("count: ${presence.count}")
inbox.unsubscribe()
subscriber.disconnect()
publisher.disconnect()
```

## What it demonstrates

- Manager discovery + automatic worker assignment (fully transparent)
- `client.channel(name)` -> `channel.subscribe(cb, opts)` -> `channel.publish(msg)`
- **Cross-client delivery**: a message published by `bob` is delivered to `alice`'s
  subscription in real time - provably through the worker, not a local echo
- Presence tracking, unsubscribe, and graceful disconnect
- A 15-second timeout so a stalled round-trip is reported as a failure (non-zero exit)

## Files

- `Dockerfile` - builds the SDK from source and runs the two-client demo on `gradle:8.5-jdk11`.
- `PROOF.txt` - captured transcript of a real containerised run against the platform.
- `src/main/kotlin/com/oddsockets/demo/Demo.kt` - the two-client round-trip program.
- `build.gradle.kts` / `settings.gradle.kts` - resolve the SDK via a Gradle composite build.
