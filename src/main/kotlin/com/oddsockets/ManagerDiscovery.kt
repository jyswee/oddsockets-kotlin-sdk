package com.oddsockets

import com.oddsockets.model.Constants
import java.net.URI

/**
 * Manager Discovery Service
 *
 * Resolves the manager endpoint that a client should talk to. The manager
 * handles all worker routing and load balancing transparently.
 *
 * Resolution order is: explicit configuration, then the `ODDSOCKETS_MANAGER_URL`
 * environment variable, then the public default endpoint. The default is only
 * ever used when nothing was configured at all - a configured manager that is
 * unreachable must surface that failure, because silently redirecting a
 * self-hosted or staging client to production makes a misconfigured deployment
 * look healthy.
 */
class ManagerDiscovery private constructor() {

    /**
     * Resolve the manager URL for a client.
     * @param apiKey The OddSockets API key
     * @param configuredManagerUrl The manager URL from the client configuration, may be null
     * @return The resolved manager URL, without any trailing slash
     * @throws IllegalArgumentException if the resolved URL is not an absolute http(s) URL
     */
    suspend fun discoverManagerUrl(apiKey: String, configuredManagerUrl: String?): String {
        return resolveManagerUrl(configuredManagerUrl)
    }

    /**
     * Clear cache (no-op, kept for compatibility)
     */
    fun clearCache() {
        // No cache to clear in simplified version
    }

    companion object {
        /**
         * Singleton instance. It holds no per-client state: the manager URL is always
         * supplied by the caller so that one client's configuration cannot leak into another's.
         */
        val instance = ManagerDiscovery()

        /** Environment variable consulted when no manager URL was configured explicitly. */
        const val MANAGER_URL_ENV_VAR = "ODDSOCKETS_MANAGER_URL"

        /**
         * Resolve and validate a manager URL.
         * @param configuredManagerUrl The manager URL from the client configuration, may be null
         * @return The resolved manager URL, without any trailing slash
         * @throws IllegalArgumentException if the resolved URL is not an absolute http(s) URL
         */
        fun resolveManagerUrl(configuredManagerUrl: String?): String {
            val candidate = configuredManagerUrl?.takeIf { it.isNotBlank() }
                ?: System.getenv(MANAGER_URL_ENV_VAR)?.takeIf { it.isNotBlank() }
                ?: Constants.DEFAULT_MANAGER_URL

            return validateManagerUrl(candidate)
        }

        private fun validateManagerUrl(managerUrl: String): String {
            val trimmed = managerUrl.trim()

            val uri = runCatching { URI(trimmed) }.getOrElse {
                throw IllegalArgumentException("Invalid managerUrl: $managerUrl")
            }

            val scheme = uri.scheme?.lowercase()
            require(uri.isAbsolute && uri.host != null && (scheme == "http" || scheme == "https")) {
                "Invalid managerUrl: $managerUrl"
            }

            return trimmed.trimEnd('/')
        }
    }
}
