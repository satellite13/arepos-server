package ru.kavader.arepos.security

import org.springframework.stereotype.Service
import ru.kavader.arepos.config.CerbosProperties
import java.util.UUID

data class CerbosAccessRequest(
    val resourceKind: String,
    val action: String,
    val resourceId: UUID,
    val ownerId: UUID? = null
)

@Service
class CerbosDecisionService(
    private val cerbosProperties: CerbosProperties
) {
    /**
     * Placeholder for Cerbos PDP integration.
     * Returns null while Cerbos check is not implemented yet.
     */
    fun check(request: CerbosAccessRequest): Boolean? {
        if (!cerbosProperties.enabled) {
            return null
        }
        return null
    }
}
