package com.fieldops.app.network

/**
 * Fetch every page of a paged endpoint and return the flattened list.
 *
 * Loops the continuation-token chain (capped at 20 pages so a mis-behaving
 * endpoint can't spin forever). Callers pass a lambda that, given a token,
 * returns the next `PagedResponse<T>` or null on failure.
 *
 * Typical usage:
 *     val tasks = drainPaged { token ->
 *         apiService.getTasks(continuationToken = token).body()
 *     }
 */
suspend fun <T> drainPaged(
    maxPages: Int = 20,
    fetch: suspend (String?) -> PagedResponse<T>?
): List<T> {
    val collected = mutableListOf<T>()
    var token: String? = null
    for (i in 0 until maxPages) {
        val page = fetch(token) ?: break
        page.items?.let { collected.addAll(it) }
        token = page.continuationToken
        if (token.isNullOrEmpty()) break
    }
    return collected
}
