package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class SearchServiceTest {
    @Mock lateinit var modelsRepository: ModelsRepository
    @Mock lateinit var notationsRepository: NotationsRepository
    @Mock lateinit var nodesRepository: NodesRepository
    @Mock lateinit var linksRepository: LinksRepository
    @Mock lateinit var diagramsRepository: DiagramsRepository
    @Mock lateinit var componentsRepository: ComponentsRepository
    @Mock lateinit var relationsRepository: RelationsRepository
    @Mock lateinit var accessService: ResourceAccessService

    @Test
    fun `diagram search uses bounded slim projection page`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "Model",
            createdAt = Instant.now(),
            version = "1.0.0",
            owner = owner
        )
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        `when`(
            diagramsRepository.searchLatestActiveByModelIdAndName(
                model.id!!,
                "diagram",
                PageRequest.of(0, 7)
            )
        ).thenReturn(PageImpl(emptyList()))

        service().searchModel(model.id!!, "diagram", "diagrams", 7)

        verify(diagramsRepository).searchLatestActiveByModelIdAndName(
            model.id!!,
            "diagram",
            PageRequest.of(0, 7)
        )
        assertTrue(
            DiagramsRepository::class.java.methods.none { it.name == "findActiveByModelIdAndName" },
            "Diagram search must not expose an unbounded entity-list query"
        )
    }

    private fun service() = SearchService(
        modelsRepository,
        notationsRepository,
        nodesRepository,
        linksRepository,
        diagramsRepository,
        componentsRepository,
        relationsRepository,
        accessService,
        ModelSearchPathEnricher(nodesRepository, ObjectMapper()),
        ObjectMapper()
    )
}
