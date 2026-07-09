package ru.kavader.arepos.security

/**
 * Cerbos could not produce a policy decision (network/circuit/parse failure).
 * Callers must map this to HTTP 503 — not to a silent policy deny (403).
 */
class CerbosUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
