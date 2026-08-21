package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID
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

    @Test
    fun `pages graph neighbor ids without duplicating self links`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val nodeType = persistNodeType(owner)
        val firstType = persistLinkType(owner)
        val secondType = persistLinkType(owner)
        val center = persistNode(model, owner, nodeType)
        val incomingPeer = persistNode(model, owner, nodeType)
        val outgoingPeer = persistNode(model, owner, nodeType)
        val incoming = persistLink(model, owner, nodeType, firstType, incomingPeer, center)
        val outgoing = persistLink(model, owner, nodeType, firstType, center, outgoingPeer)
        val self = persistLink(model, owner, nodeType, firstType, center, center)
        persistLink(model, owner, nodeType, secondType, center, outgoingPeer)

        val both = linksRepository.findGraphNeighborIds(
            model.id!!,
            center.id!!,
            "BOTH",
            firstType.id,
            PageRequest.of(0, 10)
        )

        val expected = listOf(
            incoming.id!! to incomingPeer.id!!,
            outgoing.id!! to outgoingPeer.id!!,
            self.id!! to center.id!!
        ).sortedWith(compareBy<Pair<UUID, UUID>>({ it.first.toString() }, { it.second.toString() }))
        assertEquals(expected, both.content.map { it.getLinkId() to it.getNodeId() })
        assertEquals(3, both.totalElements)

        val incomingPage = linksRepository.findGraphNeighborIds(
            model.id!!,
            center.id!!,
            "IN",
            null,
            PageRequest.of(1, 1)
        )
        assertEquals(2, incomingPage.totalElements)
        assertEquals(1, incomingPage.content.size)
    }

    @Test
    fun `graph neighbors are model scoped`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val foreignModel = persistModel(owner)
        val nodeType = persistNodeType(owner)
        val linkType = persistLinkType(owner)
        val center = persistNode(model, owner, nodeType)
        val foreignCenter = persistNode(foreignModel, owner, nodeType)
        val foreignPeer = persistNode(foreignModel, owner, nodeType)
        persistLink(foreignModel, owner, nodeType, linkType, foreignCenter, foreignPeer)

        val result = linksRepository.findGraphNeighborIds(
            model.id!!,
            center.id!!,
            "BOTH",
            null,
            Pageable.ofSize(10)
        )

        assertTrue(result.isEmpty)
    }
}

