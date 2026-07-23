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
class ComponentsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists component bound to notation`() {
        val component = persistComponent()
        val found = componentsRepository.findById(component.id!!)
        assertTrue(found.isPresent)
        assertEquals(component.notation.id, found.get().notation.id)
    }

    @Test
    fun `findByFilters filters by tags jsonb contains all`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val nodeType = persistNodeType(owner = owner)

        persistComponent(
            notation = notation,
            owner = owner,
            nodeType = nodeType,
            name = "tagged-hit",
            attrs = """{"tags":["alpha","beta"]}"""
        )
        persistComponent(
            notation = notation,
            owner = owner,
            nodeType = nodeType,
            name = "tagged-miss",
            attrs = """{"tags":["alpha"]}"""
        )

        val result = componentsRepository.findByFilters(
            notationId = notation.id,
            ownerId = null,
            name = null,
            tagsJson = """["alpha","beta"]""",
            pageable = Pageable.unpaged()
        )

        assertEquals(1, result.totalElements)
        assertEquals("tagged-hit", result.content.first().name)
    }

    @Test
    fun `findAccessible owner sees own component`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val component = persistComponent(notation = notation, owner = owner)

        val ids = accessibleIds(currentUserId = owner.id!!)
        assertEquals(setOf(component.id), ids)
    }

    @Test
    fun `findAccessible stranger without share sees empty`() {
        val owner = persistUser()
        val stranger = persistUser()
        val notation = persistNotation(owner = owner)
        persistComponent(notation = notation, owner = owner)

        assertEquals(emptySet(), accessibleIds(currentUserId = stranger.id!!))
    }

    @Test
    fun `findAccessible notation VIEW share makes component visible`() {
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        val component = persistComponent(notation = notation, owner = owner)
        persistShare(
            resourceType = ShareResourceType.NOTATION,
            resourceId = notation.id!!,
            grantedBy = owner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(component.id), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible model VIEW share plus diagram makes component visible`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val component = persistComponent(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistDiagram(model = model, notation = notation, owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = viewer,
            permission = SharePermission.VIEW
        )

        assertEquals(setOf(component.id), accessibleIds(currentUserId = viewer.id!!))
    }

    @Test
    fun `findAccessible soft-deleted notation hides shared notation path`() {
        val owner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = owner)
        persistComponent(notation = notation, owner = owner)
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
        persistComponent(notation = notation, owner = notationOwner)
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
    fun `findAccessible diagramEditor EDIT share shows component for matching notation`() {
        val notationOwner = persistUser()
        val modelOwner = persistUser()
        val editor = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val component = persistComponent(notation = notation, owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistShare(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id!!,
            grantedBy = modelOwner,
            grantee = editor,
            permission = SharePermission.EDIT
        )

        assertEquals(
            setOf(component.id),
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
        persistComponent(notation = notation, owner = notationOwner)
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
        persistComponent(notation = notation, owner = notationOwner)
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
        componentsRepository.findAccessibleByFiltersForUser(
            notationId,
            ownerId,
            name,
            tagsJson,
            currentUserId,
            diagramEditorModelId,
            Pageable.unpaged()
        ).content.map { it.id }.toSet()
}
