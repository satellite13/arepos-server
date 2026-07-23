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
class RelationsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists relation referencing notation and link type`() {
        val relation = persistRelation()
        val found = relationsRepository.findById(relation.id!!)
        assertTrue(found.isPresent)
        assertEquals(relation.linkType.id, found.get().linkType.id)
    }

    @Test
    fun `findByFilters filters by tags jsonb contains all`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val linkType = persistLinkType(owner = owner)

        persistRelation(
            notation = notation,
            owner = owner,
            linkType = linkType,
            name = "relation-hit",
            attrs = """{"tags":["alpha","beta"]}"""
        )
        persistRelation(
            notation = notation,
            owner = owner,
            linkType = linkType,
            name = "relation-miss",
            attrs = """{"tags":["alpha"]}"""
        )

        val result = relationsRepository.findByFilters(
            notationId = notation.id,
            ownerId = null,
            name = null,
            tagsJson = """["alpha","beta"]""",
            pageable = Pageable.unpaged()
        )

        assertEquals(1, result.totalElements)
        assertEquals("relation-hit", result.content.first().name)
    }

    @Test
    fun `findAccessible owner sees own relation`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val relation = persistRelation(notation = notation, owner = owner)

        assertEquals(setOf(relation.id), accessibleIds(currentUserId = owner.id!!))
    }

    @Test
    fun `findAccessible stranger without share sees empty`() {
        val owner = persistUser()
        val stranger = persistUser()
        val notation = persistNotation(owner = owner)
        persistRelation(notation = notation, owner = owner)

        assertEquals(emptySet(), accessibleIds(currentUserId = stranger.id!!))
    }

    @Test
    fun `findAccessible notation VIEW share makes relation visible`() {
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        val relation = persistRelation(notation = notation, owner = owner)
        persistShare(
            resourceType = ShareResourceType.NOTATION,
            resourceId = notation.id!!,
            grantedBy = owner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(relation.id), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible model VIEW share plus diagram makes relation visible`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val relation = persistRelation(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistDiagram(model = model, notation = notation, owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(relation.id), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible soft-deleted notation hides shared notation path`() {
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        persistRelation(notation = notation, owner = owner)
        persistShare(
            resourceType = ShareResourceType.NOTATION,
            resourceId = notation.id!!,
            grantedBy = owner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )
        notationsRepository.softDeleteById(notation.id!!)

        assertEquals(emptySet(), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible soft-deleted diagram hides model share path`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        persistRelation(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        val diagram = persistDiagram(model = model, notation = notation, owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )
        diagramsRepository.softDeleteById(diagram.id!!)

        assertEquals(emptySet(), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible diagramEditor EDIT share shows relation for matching notation`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val editor = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val relation = persistRelation(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = editor,
            permission = SharePermission.EDIT
        )

        assertEquals(
            setOf(relation.id),
            accessibleIds(
                notationId = notation.id,
                currentUserId = editor.id!!,
                diagramEditorModelId = model.id
            )
        )
    }

    @Test
    fun `findAccessible diagramEditor VIEW-only share does not unlock editor branch`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        persistRelation(notation = notation, owner = notationOwner)
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
            accessibleIds(
                notationId = notation.id,
                currentUserId = viewer.id!!,
                diagramEditorModelId = model.id
            )
        )
    }

    @Test
    fun `findAccessible diagramEditor branch inactive when diagramEditorModelId is null`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val editor = persistUser()
        val notation = persistNotation(owner = notationOwner)
        persistRelation(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = editor,
            permission = SharePermission.EDIT
        )

        assertEquals(
            emptySet(),
            accessibleIds(
                notationId = notation.id,
                currentUserId = editor.id!!,
                diagramEditorModelId = null
            )
        )
    }

    private fun accessibleIds(
        notationId: UUID? = null,
        ownerId: UUID? = null,
        name: String? = null,
        tagsJson: String? = null,
        currentUserId: UUID,
        diagramEditorModelId: UUID? = null
    ): Set<UUID?> =
        relationsRepository.findAccessibleByFiltersForUser(
            notationId,
            ownerId,
            name,
            tagsJson,
            currentUserId,
            diagramEditorModelId,
            Pageable.unpaged()
        ).content.map { it.id }.toSet()
}
