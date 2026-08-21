package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinksRepositoryTest : RepositoryTestBase() {

    @Test
    fun `creates link between nodes of same model`() {
        val link = persistLink()
        val found = linksRepository.findById(link.id!!)
        assertTrue(found.isPresent)
        assertEquals(link.model.id, found.get().model.id)
    }

    @Test
    fun `finds model-scoped links incident to endpoint ids in stable order`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val foreignModel = persistModel(owner)
        val nodeType = persistNodeType(owner)
        val linkType = persistLinkType(owner)
        val endpoint = persistNode(model, owner, nodeType)
        val firstPeer = persistNode(model, owner, nodeType)
        val secondPeer = persistNode(model, owner, nodeType)
        val first = persistLink(model, owner, nodeType, linkType, endpoint, firstPeer)
        val second = persistLink(model, owner, nodeType, linkType, secondPeer, endpoint)
        val foreignSource = persistNode(foreignModel, owner, nodeType)
        val foreignTarget = persistNode(foreignModel, owner, nodeType)
        persistLink(
            foreignModel,
            owner,
            nodeType,
            linkType,
            foreignSource,
            foreignTarget
        )

        val foundIds = linksRepository.findIdsByModelIdAndEndpointNodeIds(
            model.id!!,
            listOf(endpoint.id!!, foreignSource.id!!),
            Pageable.ofSize(10)
        )

        assertEquals(
            listOf(first.id!!, second.id!!).sortedBy { it.toString() },
            foundIds
        )
    }
}

