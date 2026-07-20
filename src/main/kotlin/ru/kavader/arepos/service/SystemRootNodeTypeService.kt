package ru.kavader.arepos.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodeTypesRepository
import java.time.Instant

@Service
class SystemRootNodeTypeService(
    private val nodeTypesRepository: NodeTypesRepository
) {
    companion object {
        private const val SYSTEM_ROOT_NODE_TYPE_NAME = "Directory"
        private const val SYSTEM_ROOT_NODE_TYPE_ATTRS = """{"system":{"hiddenTreeRootType":true}}"""
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getOrCreate(owner: Users, now: Instant): NodeTypes {
        nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, SYSTEM_ROOT_NODE_TYPE_NAME)?.let { return it }

        return try {
            nodeTypesRepository.save(
                NodeTypes(
                    name = SYSTEM_ROOT_NODE_TYPE_NAME,
                    createdAt = now,
                    updatedAt = now,
                    attrs = SYSTEM_ROOT_NODE_TYPE_ATTRS,
                    owner = owner
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, SYSTEM_ROOT_NODE_TYPE_NAME)
                ?: throw ex
        }
    }
}
