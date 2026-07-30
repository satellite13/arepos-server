package ru.kavader.arepos.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@Service
class SystemRootNodeTypeService(
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository
) {
    companion object {
        const val SYSTEM_OWNER_EMAIL = "system@arepos.local"
        private const val SYSTEM_ROOT_NODE_TYPE_NAME = "Directory"
        private const val SYSTEM_ROOT_NODE_TYPE_ATTRS = """{"system":{"hiddenTreeRootType":true}}"""
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

    private fun findSystemDirectory(): NodeTypes? =
        nodeTypesRepository.findByOwnerEmailIgnoreCaseAndNameIgnoreCase(
            ownerEmail = SYSTEM_OWNER_EMAIL,
            name = SYSTEM_ROOT_NODE_TYPE_NAME
        )
}
