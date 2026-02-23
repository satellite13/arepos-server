package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
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
}

