package com.oddsockets.demo

import com.oddsockets.OddSocketsClient
import com.oddsockets.config.OddSocketsConfig
import com.oddsockets.model.subscribeOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * OddSockets Kotlin SDK - runnable two-client demo.
 *
 * Two genuine end-to-end round-trips, each using TWO independent clients:
 *   1. Core pub/sub: a SUBSCRIBER ("alice") receives a message a PUBLISHER
 *      ("bob") sends on its own connection.
 *   2. Enhanced events: bob fires enhanced.startTyping + enhanced.addReaction
 *      and alice receives "user_typing" + "reaction_added" on her public raw
 *      event surface (client.on).
 *
 * Because the two clients are separate connections, anything alice receives can
 * ONLY have travelled through the OddSockets worker - it cannot be a local echo.
 * Uses the SAME SDK a consumer installs. No mocks.
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

    if (!basicRoundTrip(apiKey)) exitProcess(2)
    println()
    if (!enhancedRoundTrip(apiKey)) exitProcess(3)

    exitProcess(0)
}

// ---- Scenario 1: core pub/sub cross-client round-trip ----------------------
private suspend fun basicRoundTrip(apiKey: String): Boolean {
    val channelName = "demo-${Random.nextInt(100_000, 999_999)}"
    val nonce = "n-${Random.nextLong(0, Long.MAX_VALUE)}"

    val subscriber = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "alice", autoConnect = false)
    )
    val publisher = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "bob", autoConnect = false)
    )

    val roundTrip = CompletableDeferred<Unit>()

    val result = withTimeoutOrNull(TIMEOUT_MILLIS) {
        println("[connect] connecting both clients...")
        subscriber.connect()
        publisher.connect()
        println("[connect] alice = ${subscriber.getState()}, bob = ${publisher.getState()}")

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

        val outbox = publisher.channel(channelName)
        val ack = outbox.publish(DemoMessage("hello from bob", nonce, "bob"))
        println("[bob] published, messageId = ${ack.messageId}")

        roundTrip.await()

        val presence = inbox.getPresence()
        println("[alice] presence: ${presence.count} user(s).")
        inbox.unsubscribe()
        println("[alice] unsubscribed.")
    }

    subscriber.disconnect()
    publisher.disconnect()
    subscriber.close()
    publisher.close()

    return if (result != null) {
        println("\nOK - cross-client round-trip verified on $channelName")
        true
    } else {
        System.err.println("\nTIMEOUT - no cross-client delivery within ${TIMEOUT_MILLIS / 1000}s")
        false
    }
}

// ---- Scenario 2: enhanced-events cross-client receive-path -----------------
private suspend fun enhancedRoundTrip(apiKey: String): Boolean {
    val channelName = "enh-${Random.nextInt(100_000, 999_999)}"

    val subscriber = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "alice", autoConnect = false)
    )
    val publisher = OddSocketsClient(
        OddSocketsConfig(apiKey = apiKey, userId = "bob", autoConnect = false)
    )

    val typingSeen = CompletableDeferred<Unit>()
    val reactionSeen = CompletableDeferred<Unit>()

    // alice listens on her PUBLIC raw event surface - these can only fire if the
    // broadcast crossed the worker from bob's separate connection.
    subscriber.on("user_typing") { data ->
        val userId = stringProp(data, "userId")
        if (userId == "bob") {
            println("[alice] received 'user_typing' from bob - broadcast round-trip.")
            typingSeen.complete(Unit)
        }
    }
    subscriber.on("reaction_added") { _ ->
        println("[alice] received 'reaction_added' (:thumbsup:) from bob - broadcast round-trip.")
        reactionSeen.complete(Unit)
    }

    val result = withTimeoutOrNull(TIMEOUT_MILLIS) {
        println("[connect] connecting both clients...")
        subscriber.connect()
        publisher.connect()

        val aliceRoom = subscriber.channel(channelName)
        val bobRoom = publisher.channel(channelName)
        aliceRoom.subscribe({ }, subscribeOptions { enablePresence(true) })
        bobRoom.subscribe({ }, subscribeOptions { enablePresence(true) })
        println("[both] subscribed to $channelName")

        // bob publishes a message so there is a real messageId to react to.
        val ack = bobRoom.publish(DemoMessage("reactable", "n/a", "bob"))
        println("[bob] published messageId=${ack.messageId}")

        println("[bob] enhanced.startTyping(bob) ...")
        publisher.enhanced.startTyping("bob", channelName)

        println("[bob] enhanced.addReaction :thumbsup: ...")
        publisher.enhanced.addReaction(ack.messageId, channelName, ":thumbsup:", "bob", "Bob")

        awaitAll(typingSeen, reactionSeen)
    }

    subscriber.disconnect()
    publisher.disconnect()
    subscriber.close()
    publisher.close()

    return if (result != null) {
        println("\nOK - enhanced broadcast receive-path verified (user_typing + reaction_added)")
        true
    } else {
        System.err.println("\nTIMEOUT - enhanced broadcasts not received within ${TIMEOUT_MILLIS / 1000}s")
        false
    }
}

private fun stringProp(data: JsonElement?, name: String): String? =
    runCatching { data?.jsonObject?.get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()

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
