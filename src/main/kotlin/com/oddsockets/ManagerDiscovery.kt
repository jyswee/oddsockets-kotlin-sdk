package com.oddsockets

/**
 * Simple Manager Discovery Service
 * 
 * Always connects to the main manager endpoint which handles
 * all routing and load balancing transparently.
 */
class ManagerDiscovery {
    private val managerUrl = "https://connect.oddsockets.tyga.network"
    
    /**
     * Get the manager URL (always returns the main endpoint)
     * @param apiKey The OddSockets API key (not used, kept for compatibility)
     * @return The manager URL
     */
    suspend fun discoverManagerUrl(apiKey: String): String {
        return managerUrl
    }
    
    /**
     * Clear cache (no-op, kept for compatibility)
     */
    fun clearCache() {
        // No cache to clear in simplified version
    }
    
    companion object {
        /**
         * Singleton instance
         */
        val instance = ManagerDiscovery()
    }
}
