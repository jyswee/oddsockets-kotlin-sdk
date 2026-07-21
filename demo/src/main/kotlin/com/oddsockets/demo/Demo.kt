package com.oddsockets.demo

import com.oddsockets.OddSocketsClient
import com.oddsockets.config.OddSocketsConfig
import com.oddsockets.model.EventType
import com.oddsockets.model.subscribeOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * OddSockets Kotlin SDK - runnable two-client demo.
 *
 * A genuine end-to-end round-trip using TWO independent clients:
 *   - a SUBSCRIBER (user "alice") that listens on a channel
 *   - a PUBLISHER  (user "bob")   that sends one message
 *
 * Because they are separate connections, a message reaching the subscriber can
 * ONLY have travelled through the OddSockets worker - it cannot be a local echo.
 * A matched nonce here is proof of a real Socket.IO round-trip. No mocks.
 *
 * Exercised surface: connect -> subscribe (+presence) -> publish -> receive
 * -> presence -> unsubscribe -> disconnect.
 */
@Serializable
data class DemoMessage(val text: String, val nonce: String, val from: String)

private const val TIMEOUT_MILLIS = 15_000L

fun main(): Unit = runBlocking {
    val apiKey = System.getenv("ODDSOCKETS_API_KEY")
    if (apiKey.isNullOrBlank()) {
        printSignupInstructions()
        exitProcess(1)
    }

    // A unique channel and nonce so we only ever match our own run.
    val channelName = "demo-${Random.nextInt(100_000, 999_999)}"
    val nonce = "n-${Random.nextLong(0, Long.MAX_VALUE)}"

    // Two independent clients on the same platform.
    val subscriber = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "alice", autoConnect = false)
    )
    val publisher = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "bob", autoConnect = false)
    )

    subscriber.on(EventType.WORKER_ASSIGNED) { data ->
        val worker = (data as? Map<*, *>)?.get("workerId")
        println("[alice] worker $worker")
    }
    publisher.on(EventType.WORKER_ASSIGNED) { data ->
        val worker = (data as? Map<*, *>)?.get("workerId")
        println("[bob]   worker $worker")
    }

    // Completed as soon as alice sees bob's message (matching nonce).
    val roundTrip = CompletableDeferred<Unit>()

    val result = withTimeoutOrNull(TIMEOUT_MILLIS) {
        println("[connect] connecting both clients...")
        subscriber.connect()
        publisher.connect()
        println("[connect] alice = ${subscriber.getState()}, bob = ${publisher.getState()}")

        // Subscriber joins with presence enabled.
        val inbox = subscriber.channel(channelName)
        inbox.subscribe({ message ->
            val received = message.data?.let { data ->
                runCatching { data.jsonObject["nonce"]?.jsonPrimitive?.content }.getOrNull()
            }
            if (received == nonce) {
                println("[alice] received bob's message (nonce matched) - real round-trip.")
                roundTrip.complete(Unit)
            }
        }, subscribeOptions { enablePresence(true) })
        println("[alice] subscribed to $channelName (presence on)")

        // Publisher sends from its OWN connection.
        val outbox = publisher.channel(channelName)
        val ack = outbox.publish(DemoMessage("hello from bob", nonce, "bob"))
        println("[bob] published, messageId = ${ack.messageId}")

        // Wait for the cross-client delivery.
        roundTrip.await()

        // Inspect presence, then tear down cleanly.
        val presence = inbox.getPresence()
        println("[alice] presence: ${presence.count} user(s).")
        inbox.unsubscribe()
        println("[alice] unsubscribed.")
    }

    subscriber.disconnect()
    publisher.disconnect()
    subscriber.close()
    publisher.close()

    if (result != null) {
        println("\nOK - cross-client round-trip verified")
        exitProcess(0)
    } else {
        System.err.println("\nTIMEOUT - no cross-client delivery within ${TIMEOUT_MILLIS / 1000}s")
        exitProcess(2)
    }
}

private fun printSignupInstructions() {
    System.err.println(
        """
        ODDSOCKETS_API_KEY is not set.

        Get a free API key (two-step, no card required):

          curl -X POST https://oddsockets.com/api/agent-signup \
            -H "Content-Type: application/json" \
            -d '{"email": "you@example.com", "agentName": "kotlin-demo", "platform": "kotlin"}'

          # A 6-digit code is emailed to you. Verify it:
          curl -X POST https://oddsockets.com/api/agent-signup/verify \
            -H "Content-Type: application/json" \
            -d '{"email": "you@example.com", "code": "123456", "agentName": "kotlin-demo"}'

        Then export the returned key and run again:

          export ODDSOCKETS_API_KEY=ak_your_key_here
          ./gradlew run
        """.trimIndent()
    )
}
