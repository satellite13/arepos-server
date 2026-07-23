package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationRulesRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists relation rule for components`() {
        val rule = persistRelationRule()

        val found = relationRulesRepository.findById(rule.id!!)
        assertTrue(found.isPresent)
        assertEquals(rule.relation.id, found.get().relation.id)
    }

    @Test
    fun `findByFilters filters by notationId`() {
        val owner = persistUser()
        val notationA = persistNotation(owner = owner)
        val notationB = persistNotation(owner = owner)
        val nodeType = persistNodeType(owner = owner)
        val linkType = persistLinkType(owner = owner)

        val relationA = persistRelation(notation = notationA, linkType = linkType, owner = owner)
        val relationB = persistRelation(notation = notationB, linkType = linkType, owner = owner)

        val fromA = persistComponent(notation = notationA, nodeType = nodeType, owner = owner)
        val toA = persistComponent(notation = notationA, nodeType = nodeType, owner = owner)
        val fromB = persistComponent(notation = notationB, nodeType = nodeType, owner = owner)
        val toB = persistComponent(notation = notationB, nodeType = nodeType, owner = owner)

        persistRelationRule(relation = relationA, fromComponent = fromA, toComponent = toA)
        persistRelationRule(relation = relationB, fromComponent = fromB, toComponent = toB)

        val filtered = relationRulesRepository.findByFilters(
            relationId = null,
            ownerId = null,
            notationId = notationA.id,
            pageable = Pageable.unpaged()
        )

        assertEquals(1, filtered.totalElements)
        assertEquals(relationA.id, filtered.content.first().relation.id)
    }

    @Test
    fun `admin list methods return same ids for same filters`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = owner),
            fromComponent = persistComponent(notation = notation, owner = owner),
            toComponent = persistComponent(notation = notation, owner = owner)
        )

        val entityIds = relationRulesRepository.findByFilters(
            relationId = null,
            ownerId = null,
            notationId = notation.id,
            pageable = Pageable.unpaged()
        ).content.map { it.id }.toSet()

        val projectedIds = relationRulesRepository.findProjectedByFilters(
            relationId = null,
            ownerId = null,
            notationId = notation.id,
            pageable = Pageable.unpaged()
        ).content.map { it.id }.toSet()

        val lightIds = relationRulesRepository.findProjectedLightByFilters(
            relationId = null,
            ownerId = null,
            notationId = notation.id,
            pageable = Pageable.unpaged()
        ).content.map { it.id }.toSet()

        assertEquals(setOf(rule.id), entityIds)
        assertEquals(entityIds, projectedIds)
        assertEquals(entityIds, lightIds)
    }

    @Test
    fun `forUser owner sees rule and light matches full projection ids`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = owner),
            fromComponent = persistComponent(notation = notation, owner = owner),
            toComponent = persistComponent(notation = notation, owner = owner)
        )

        val full = forUserIds(currentUserId = owner.id!!)
        val light = forUserLightIds(currentUserId = owner.id!!)
        assertEquals(setOf(rule.id), full)
        assertEquals(full, light)
    }

    @Test
    fun `forUser stranger without share sees empty`() {
        val owner = persistUser()
        val stranger = persistUser()
        val notation = persistNotation(owner = owner)
        persistRelationRule(
            relation = persistRelation(notation = notation, owner = owner),
            fromComponent = persistComponent(notation = notation, owner = owner),
            toComponent = persistComponent(notation = notation, owner = owner)
        )

        assertEquals(emptySet(), forUserIds(currentUserId = stranger.id!!))
    }

    @Test
    fun `forUser notation VIEW share makes rule visible`() {
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = owner),
            fromComponent = persistComponent(notation = notation, owner = owner),
            toComponent = persistComponent(notation = notation, owner = owner)
        )
        persistShare(
            resourceType = ShareResourceType.NOTATION,
            resourceId = notation.id!!,
            grantedBy = owner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(rule.id), forUserIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `forUser model VIEW share plus diagram makes rule visible`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = notationOwner),
            fromComponent = persistComponent(notation = notation, owner = notationOwner),
            toComponent = persistComponent(notation = notation, owner = notationOwner)
        )
        val model = persistModel(owner = modelOwner)
        persistDiagram(model = model, notation = notation, owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(rule.id), forUserIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `forUser diagramEditor EDIT share shows rule for matching notation`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val editor = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = notationOwner),
            fromComponent = persistComponent(notation = notation, owner = notationOwner),
            toComponent = persistComponent(notation = notation, owner = notationOwner)
        )
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = editor,
            permission = SharePermission.EDIT
        )

        assertEquals(
            setOf(rule.id),
            forUserIds(
                notationId = notation.id,
                currentUserId = editor.id!!,
                diagramEditorModelId = model.id
            )
        )
    }

    @Test
    fun `forUser diagramEditor VIEW-only share does not unlock editor branch`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        persistRelationRule(
            relation = persistRelation(notation = notation, owner = notationOwner),
            fromComponent = persistComponent(notation = notation, owner = notationOwner),
            toComponent = persistComponent(notation = notation, owner = notationOwner)
        )
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(
            emptySet(),
            forUserIds(
                notationId = notation.id,
                currentUserId = viewer.id!!,
                diagramEditorModelId = model.id
            )
        )
    }

    @Test
    fun `forUser diagramEditor soft-deleted model returns empty`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val editor = persistUser()
        val notation = persistNotation(owner = notationOwner)
        persistRelationRule(
            relation = persistRelation(notation = notation, owner = notationOwner),
            fromComponent = persistComponent(notation = notation, owner = notationOwner),
            toComponent = persistComponent(notation = notation, owner = notationOwner)
        )
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = editor,
            permission = SharePermission.EDIT
        )
        modelsRepository.softDeleteById(model.id!!)

        assertEquals(
            emptySet(),
            forUserIds(
                notationId = notation.id,
                currentUserId = editor.id!!,
                diagramEditorModelId = model.id
            )
        )
    }

    @Test
    fun `forUser soft-deleted notation still visible via share path under current SQL`() {
        // RelationRules ForUser notation IN-subquery does not filter n.deleted — preserve that quirk.
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        val rule = persistRelationRule(
            relation = persistRelation(notation = notation, owner = owner),
            fromComponent = persistComponent(notation = notation, owner = owner),
            toComponent = persistComponent(notation = notation, owner = owner)
        )
        persistShare(
            resourceType = ShareResourceType.NOTATION,
            resourceId = notation.id!!,
            grantedBy = owner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )
        notationsRepository.softDeleteById(notation.id!!)

        assertEquals(setOf(rule.id), forUserIds(currentUserId = viewer.id!!))
    }

    private fun forUserIds(
        relationId: UUID? = null,
        ownerId: UUID? = null,
        notationId: UUID? = null,
        currentUserId: UUID,
        diagramEditorModelId: UUID? = null
    ): Set<UUID?> =
        relationRulesRepository.findProjectedByFiltersForUser(
            relationId,
            ownerId,
            notationId,
            currentUserId,
            diagramEditorModelId,
            Pageable.unpaged()
        ).content.map { it.id }.toSet()

    private fun forUserLightIds(
        relationId: UUID? = null,
        ownerId: UUID? = null,
        notationId: UUID? = null,
        currentUserId: UUID,
        diagramEditorModelId: UUID? = null
    ): Set<UUID?> =
        relationRulesRepository.findProjectedLightByFiltersForUser(
            relationId,
            ownerId,
            notationId,
            currentUserId,
            diagramEditorModelId,
            Pageable.unpaged()
        ).content.map { it.id }.toSet()
}
