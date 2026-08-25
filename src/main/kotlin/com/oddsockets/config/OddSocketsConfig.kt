package com.oddsockets.config

import com.oddsockets.ManagerDiscovery
import com.oddsockets.model.Constants
import com.oddsockets.model.OddSocketsToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration options for the OddSockets client.
 *
 * This data class holds all configuration parameters needed to connect to OddSockets.
 * Use [OddSocketsConfigBuilder] for a fluent configuration experience.
 *
 * @property apiKey The OddSockets API key (required)
 * @property managerUrl The manager URL. When not set explicitly it falls back to the
 *   ODDSOCKETS_MANAGER_URL environment variable and then to the public default endpoint.
 *   A URL set here is always used verbatim; the client never substitutes the default
 *   endpoint when the configured manager is unreachable.
 * @property userId The user identifier (optional, auto-generated if not provided)
 * @property autoConnect Whether the client should auto-connect on creation (default: true)
 * @property reconnectAttempts The maximum number of reconnection attempts (default: 5)
 * @property heartbeatInterval The heartbeat interval (default: 30 seconds)
 * @property timeout The request timeout (default: 10 seconds)
 * @property tokenProvider A callback that mints a short-lived realtime token
 *   (FEAT-2026-0824-0040). When set, the client authenticates with a freshly
 *   resolved token instead of a static [apiKey] and silently refreshes it ahead
 *   of expiry. Not serialized.
 * @property tokenRefreshLeadMs How far ahead of expiry the token is refreshed,
 *   in milliseconds (default: 120000 / two minutes).
 */
@Serializable
data class OddSocketsConfig(
    val apiKey: String = "",
    val managerUrl: String = ManagerDiscovery.resolveManagerUrl(null),
    val userId: String? = null,
    val autoConnect: Boolean = true,
    val reconnectAttempts: Int = 5,
    @Serializable(with = DurationSerializer::class)
    val heartbeatInterval: Duration = 30.seconds,
    @Serializable(with = DurationSerializer::class)
    val timeout: Duration = 10.seconds,
    @Transient
    val tokenProvider: (suspend () -> OddSocketsToken)? = null,
    val tokenRefreshLeadMs: Long = 120_000L
) {

    /**
     * Validates the configuration.
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        // In token mode (a tokenProvider is set) no static API key is required or
        // expected; auth is carried by the freshly minted token instead.
        if (tokenProvider == null) {
            require(apiKey.isNotBlank()) { "API key is required" }
            require(apiKey.startsWith("ak_")) { "Invalid API key format" }
        }
        require(managerUrl.isNotBlank()) { "Manager URL is required" }
        ManagerDiscovery.resolveManagerUrl(managerUrl)

        require(reconnectAttempts >= 0) { "Reconnect attempts must be non-negative" }
        require(heartbeatInterval.isPositive()) { "Heartbeat interval must be positive" }
        require(timeout.isPositive()) { "Timeout must be positive" }
    }
    
    companion object {
        /**
         * Creates a configuration with just an API key using default values.
         * @param apiKey Your OddSockets API key
         * @return A configured OddSocketsConfig instance
         */
        fun default(apiKey: String): OddSocketsConfig = OddSocketsConfig(apiKey = apiKey)
        
        /**
         * Creates a builder with an API key pre-set.
         * @param apiKey Your OddSockets API key
         * @return A builder instance with the API key set
         */
        fun builder(apiKey: String): OddSocketsConfigBuilder = OddSocketsConfigBuilder().apiKey(apiKey)

        /**
         * Creates a builder authenticated with a minted-token provider instead of
         * a static API key (FEAT-2026-0824-0040). Suitable for game/app clients
         * that must not ship a long-lived key.
         * @param tokenProvider A callback that mints a fresh realtime token
         * @return A builder instance with the token provider set
         */
        fun builderWithTokenProvider(tokenProvider: suspend () -> OddSocketsToken): OddSocketsConfigBuilder =
            OddSocketsConfigBuilder().tokenProvider(tokenProvider)
    }
}

/**
 * Builder class for creating OddSocketsConfig instances using a fluent interface.
 *
 * This builder provides a Kotlin-idiomatic way to construct configuration objects
 * with method chaining and sensible defaults.
 */
class OddSocketsConfigBuilder {
    private var apiKey: String = ""
    private var managerUrl: String? = null
    private var userId: String? = null
    private var autoConnect: Boolean = true
    private var reconnectAttempts: Int = 5
    private var heartbeatInterval: Duration = 30.seconds
    private var timeout: Duration = 10.seconds
    private var tokenProvider: (suspend () -> OddSocketsToken)? = null
    private var tokenRefreshLeadMs: Long = 120_000L

    /**
     * Sets the API key.
     * @param apiKey The API key
     * @return The builder instance for chaining
     */
    fun apiKey(apiKey: String): OddSocketsConfigBuilder = apply {
        this.apiKey = apiKey
    }

    /**
     * Sets the minted-token provider (FEAT-2026-0824-0040). When set, the client
     * authenticates with freshly minted tokens instead of a static API key.
     * @param tokenProvider A callback that mints a fresh realtime token
     * @return The builder instance for chaining
     */
    fun tokenProvider(tokenProvider: suspend () -> OddSocketsToken): OddSocketsConfigBuilder = apply {
        this.tokenProvider = tokenProvider
    }

    /**
     * Sets how far ahead of expiry the minted token is refreshed, in milliseconds.
     * @param leadMs The refresh lead time in milliseconds (default 120000)
     * @return The builder instance for chaining
     */
    fun tokenRefreshLeadMs(leadMs: Long): OddSocketsConfigBuilder = apply {
        this.tokenRefreshLeadMs = leadMs
    }
    
    /**
     * Sets the manager URL.
     *
     * When left unset the ODDSOCKETS_MANAGER_URL environment variable is used,
     * falling back to the public default endpoint.
     *
     * @param managerUrl The manager URL, must be an absolute http(s) URL
     * @return The builder instance for chaining
     */
    fun managerUrl(managerUrl: String): OddSocketsConfigBuilder = apply {
        this.managerUrl = managerUrl
    }
    
    /**
     * Sets the user ID.
     * @param userId The user ID
     * @return The builder instance for chaining
     */
    fun userId(userId: String): OddSocketsConfigBuilder = apply {
        this.userId = userId
    }
    
    /**
     * Sets whether to auto-connect.
     * @param autoConnect Whether to auto-connect
     * @return The builder instance for chaining
     */
    fun autoConnect(autoConnect: Boolean = true): OddSocketsConfigBuilder = apply {
        this.autoConnect = autoConnect
    }
    
    /**
     * Sets the reconnect attempts.
     * @param attempts The number of reconnect attempts
     * @return The builder instance for chaining
     */
    fun reconnectAttempts(attempts: Int): OddSocketsConfigBuilder = apply {
        this.reconnectAttempts = attempts
    }
    
    /**
     * Sets the heartbeat interval.
     * @param interval The heartbeat interval
     * @return The builder instance for chaining
     */
    fun heartbeatInterval(interval: Duration): OddSocketsConfigBuilder = apply {
        this.heartbeatInterval = interval
    }
    
    /**
     * Sets the heartbeat interval in seconds.
     * @param seconds The heartbeat interval in seconds
     * @return The builder instance for chaining
     */
    fun heartbeatInterval(seconds: Long): OddSocketsConfigBuilder = apply {
        this.heartbeatInterval = seconds.seconds
    }
    
    /**
     * Sets the timeout.
     * @param timeout The timeout duration
     * @return The builder instance for chaining
     */
    fun timeout(timeout: Duration): OddSocketsConfigBuilder = apply {
        this.timeout = timeout
    }
    
    /**
     * Sets the timeout in seconds.
     * @param seconds The timeout in seconds
     * @return The builder instance for chaining
     */
    fun timeout(seconds: Long): OddSocketsConfigBuilder = apply {
        this.timeout = seconds.seconds
    }
    
    /**
     * Sets common development configuration.
     * @return The builder instance for chaining
     */
    fun development(): OddSocketsConfigBuilder = apply {
        managerUrl("http://localhost:3001")
        timeout(30.seconds)
        heartbeatInterval(10.seconds)
    }
    
    /**
     * Sets common production configuration.
     * @return The builder instance for chaining
     */
    fun production(): OddSocketsConfigBuilder = apply {
        managerUrl(Constants.DEFAULT_MANAGER_URL)
        timeout(10.seconds)
        heartbeatInterval(30.seconds)
    }
    
    /**
     * Builds the configuration.
     * @return The configured OddSocketsConfig instance
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun build(): OddSocketsConfig {
        val config = OddSocketsConfig(
            apiKey = apiKey,
            managerUrl = ManagerDiscovery.resolveManagerUrl(managerUrl),
            userId = userId,
            autoConnect = autoConnect,
            reconnectAttempts = reconnectAttempts,
            heartbeatInterval = heartbeatInterval,
            timeout = timeout,
            tokenProvider = tokenProvider,
            tokenRefreshLeadMs = tokenRefreshLeadMs
        )
        
        config.validate()
        return config
    }
}

// Extension functions for more idiomatic Kotlin usage
/**
 * Creates an OddSocketsConfig using a builder DSL.
 * @param apiKey The API key
 * @param block The configuration block
 * @return The configured OddSocketsConfig instance
 */
inline fun oddSocketsConfig(apiKey: String, block: OddSocketsConfigBuilder.() -> Unit = {}): OddSocketsConfig {
    return OddSocketsConfigBuilder().apiKey(apiKey).apply(block).build()
}

/**
 * Creates an OddSocketsConfig for development with sensible defaults.
 * @param apiKey The API key
 * @param block Additional configuration
 * @return The configured OddSocketsConfig instance
 */
inline fun developmentConfig(apiKey: String, block: OddSocketsConfigBuilder.() -> Unit = {}): OddSocketsConfig {
    return OddSocketsConfigBuilder().apiKey(apiKey).development().apply(block).build()
}

/**
 * Creates an OddSocketsConfig for production with sensible defaults.
 * @param apiKey The API key
 * @param block Additional configuration
 * @return The configured OddSocketsConfig instance
 */
inline fun productionConfig(apiKey: String, block: OddSocketsConfigBuilder.() -> Unit = {}): OddSocketsConfig {
    return OddSocketsConfigBuilder().apiKey(apiKey).production().apply(block).build()
}
