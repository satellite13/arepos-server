package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.CatalogTypeUpdateRequest
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@Service
class SystemRootNodeTypeService(
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val SYSTEM_OWNER_EMAIL = "system@arepos.local"
        private const val SYSTEM_ROOT_NODE_TYPE_NAME = "Directory"
        private const val SYSTEM_ROOT_NODE_TYPE_ATTRS = """{"system":{"hiddenTreeRootType":true}}"""
        private const val PROTECTED_MESSAGE = "System Directory type cannot be modified or deleted"
        private const val CUSTOM_PROPERTIES_FIELD = "customProperties"
    }

    /**
     * Returns the shared system Directory type (seeded for [SYSTEM_OWNER_EMAIL]).
     * Falls back to a per-owner Directory only when the system user/type is missing
     * (e.g. incomplete fixtures).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getOrCreate(owner: Users, now: Instant): NodeTypes {
        findSystemDirectory()?.let { return it }

        nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, SYSTEM_ROOT_NODE_TYPE_NAME)?.let { return it }

        val typeOwner = usersRepository.findByEmailIgnoreCase(SYSTEM_OWNER_EMAIL) ?: owner
        return try {
            nodeTypesRepository.save(
                NodeTypes(
                    name = SYSTEM_ROOT_NODE_TYPE_NAME,
                    createdAt = now,
                    updatedAt = now,
                    attrs = SYSTEM_ROOT_NODE_TYPE_ATTRS,
                    owner = typeOwner
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            findSystemDirectory()
                ?: nodeTypesRepository.findByOwnerAndNameIgnoreCase(typeOwner, SYSTEM_ROOT_NODE_TYPE_NAME)
                ?: nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, SYSTEM_ROOT_NODE_TYPE_NAME)
                ?: throw ex
        }
    }

    fun isProtectedSystemDirectory(nodeType: NodeTypes): Boolean {
        if (!nodeType.name.equals(SYSTEM_ROOT_NODE_TYPE_NAME, ignoreCase = true)) {
            return false
        }
        if (nodeType.owner.email.equals(SYSTEM_OWNER_EMAIL, ignoreCase = true)) {
            return true
        }
        return isSystemDirectoryAttrs(nodeType.attrs)
    }

    fun assertMutable(nodeType: NodeTypes) {
        if (isProtectedSystemDirectory(nodeType)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, PROTECTED_MESSAGE)
        }
    }

    /**
     * Allow-list for updating the protected system Directory type: the ONLY permitted
     * change is ADDING new entries to attrs.customProperties (add-if-missing). Rename,
     * owner change, removal/modification of existing customProperties and any other
     * attrs key are rejected. The regular ownership-based node-type edit permission can
     * never apply to this shared type (it belongs to the system user), so for this narrow
     * case the caller skips that check and this validation IS the security boundary.
     * Deletion stays blocked by [assertMutable] regardless.
     */
    fun assertCustomPropertiesOnlyUpdate(nodeType: NodeTypes, request: CatalogTypeUpdateRequest) {
        if (!isProtectedSystemDirectory(nodeType)) return
        request.name?.takeIf { it != nodeType.name }?.let {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "$PROTECTED_MESSAGE (rename is not allowed)")
        }
        request.ownerId?.takeIf { it != nodeType.owner.id }?.let {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "$PROTECTED_MESSAGE (owner change is not allowed)")
        }
        val nextAttrs = request.attrs ?: return
        val currentRoot = parseAttrsObject(nodeType.attrs)
        val nextRoot = parseAttrsObject(nextAttrs)
        for (field in nextRoot.fieldNames()) {
            if (field == CUSTOM_PROPERTIES_FIELD) continue
            if (currentRoot.get(field) != nextRoot.get(field)) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "$PROTECTED_MESSAGE (only additive attrs.customProperties changes are allowed)"
                )
            }
        }
        for (field in currentRoot.fieldNames()) {
            if (field != CUSTOM_PROPERTIES_FIELD && !nextRoot.has(field)) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "$PROTECTED_MESSAGE (attrs key '$field' cannot be removed)"
                )
            }
        }
        val currentProps = currentRoot.get(CUSTOM_PROPERTIES_FIELD)
        val nextProps = nextRoot.get(CUSTOM_PROPERTIES_FIELD)
        if (currentProps == null && nextProps == null) return
        if (currentProps != null && nextProps == null) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "$PROTECTED_MESSAGE (existing customProperties cannot be removed)"
            )
        }
        val currentIndex = currentProps?.groupBy { propKey(it) } ?: emptyMap()
        val nextList = nextProps as? ArrayNode
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "attrs.customProperties must be an array")
        val nextKeys = nextList.map { propKey(it) }.toSet()
        for (existingKey in currentIndex.keys) {
            if (existingKey !in nextKeys) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "$PROTECTED_MESSAGE (existing customProperties cannot be removed)"
                )
            }
        }
        for (entry in nextList) {
            val key = propKey(entry)
            val existing = currentIndex[key]?.firstOrNull()
            if (existing != null && existing != entry) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "$PROTECTED_MESSAGE (existing customProperties cannot be modified)"
                )
            }
        }
    }

    private fun parseAttrsObject(attrs: String?): ObjectNode =
        if (attrs.isNullOrBlank()) objectMapper.createObjectNode()
        else objectMapper.readTree(attrs) as? ObjectNode
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "attrs must be a JSON object")

    /** Identity of a customProperties entry: its id, falling back to its name. */
    private fun propKey(entry: JsonNode): String =
        entry.path("id").takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()
            ?: entry.path("name").asText("")

    private fun findSystemDirectory(): NodeTypes? =
        nodeTypesRepository.findByOwnerEmailIgnoreCaseAndNameIgnoreCase(
            ownerEmail = SYSTEM_OWNER_EMAIL,
            name = SYSTEM_ROOT_NODE_TYPE_NAME
        )

    private fun isSystemDirectoryAttrs(attrs: String?): Boolean {
        if (attrs.isNullOrBlank()) return false
        return try {
            val root = objectMapper.readTree(attrs)
            if (root.path("system").path("hiddenTreeRootType").asBoolean(false)) return true
            val legacySystemFlag = root.path("system").asBoolean(false)
            val legacyKindDirectory = root.path("kind").asText("").equals("directory", ignoreCase = true)
            legacySystemFlag && legacyKindDirectory
        } catch (_: Exception) {
            false
        }
    }
}
