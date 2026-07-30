package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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
