package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

@SpringBootTest
@AutoConfigureMockMvc
class RelationRulesPerformanceTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var componentsRepository: ComponentsRepository
    @Autowired lateinit var relationsRepository: RelationsRepository
    @Autowired lateinit var relationRulesRepository: RelationRulesRepository

    @Test
    fun `prints baseline p95 and payload size for heavy notation`() {
        val owner = usersRepository.save(
            Users(
                email = "perf-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val notation = createNotation(owner)
        val nodeType = createNodeType(owner)
        val linkType = createLinkType(owner)
        val components = createComponents(owner, notation, nodeType, count = 80)
        val relations = createRelations(owner, notation, linkType, count = 6)
        createRules(owner, relations, components, targetRules = 2200)

        val withAttrs = measure(ownerId = owner.id!!, notationId = notation.id!!.toString(), includeAttrs = true)
        val withoutAttrs = measure(
            ownerId = owner.id!!,
            notationId = notation.id!!.toString(),
            includeAttrs = false
        )

        println("RELATION_RULES_BASELINE includeAttrs=true rules=2200 payloadBytes=${withAttrs.payloadBytes} p50Ms=${withAttrs.p50} p95Ms=${withAttrs.p95}")
        println("RELATION_RULES_BASELINE includeAttrs=false rules=2200 payloadBytes=${withoutAttrs.payloadBytes} p50Ms=${withoutAttrs.p50} p95Ms=${withoutAttrs.p95}")
    }

    private fun percentile(sorted: List<Long>, q: Double): Long {
        if (sorted.isEmpty()) return 0
        val idx = ceil(q * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[idx]
    }

    private fun measure(ownerId: UUID, notationId: String, includeAttrs: Boolean): PerfSample {
        repeat(5) {
            mockMvc.perform(
                get("/api/v1/relation-rules")
                    .param("notationId", notationId)
                    .param("page", "0")
                    .param("size", "5000")
                    .param("includeAttrs", includeAttrs.toString())
                    .withAuth(ownerId, Role.USER)
            ).andExpect(status().isOk)
        }

        val durations = mutableListOf<Long>()
        var payloadBytes = 0
        repeat(25) {
            val start = System.nanoTime()
            val response = mockMvc.perform(
                get("/api/v1/relation-rules")
                    .param("notationId", notationId)
                    .param("page", "0")
                    .param("size", "5000")
                    .param("includeAttrs", includeAttrs.toString())
                    .withAuth(ownerId, Role.USER)
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
            durations += (System.nanoTime() - start) / 1_000_000
            payloadBytes = response.contentAsByteArray.size
        }

        val sorted = durations.sorted()
        return PerfSample(
            p50 = percentile(sorted, 0.50),
            p95 = percentile(sorted, 0.95),
            payloadBytes = payloadBytes
        )
    }

    private data class PerfSample(
        val p50: Long,
        val p95: Long,
        val payloadBytes: Int
    )

    private fun createNotation(owner: Users): Notations = notationsRepository.save(
        Notations(
            name = "perf-notation-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.now()
        )
    )

    private fun createNodeType(owner: Users): NodeTypes = nodeTypesRepository.save(
        NodeTypes(
            name = "perf-node-type-${UUID.randomUUID()}",
            owner = owner,
            createdAt = Instant.now()
        )
    )

    private fun createLinkType(owner: Users): LinkTypes = linkTypesRepository.save(
        LinkTypes(
            name = "perf-link-type-${UUID.randomUUID()}",
            owner = owner,
            createdAt = Instant.now()
        )
    )

    private fun createComponents(
        owner: Users,
        notation: Notations,
        nodeType: NodeTypes,
        count: Int
    ): List<Components> = (0 until count).map { index ->
        componentsRepository.save(
            Components(
                name = "comp-$index",
                version = "1.0.0",
                owner = owner,
                notation = notation,
                nodeType = nodeType,
                createdAt = Instant.now(),
                attrs = """{"idx":$index}"""
            )
        )
    }

    private fun createRelations(
        owner: Users,
        notation: Notations,
        linkType: LinkTypes,
        count: Int
    ): List<Relations> = (0 until count).map { index ->
        relationsRepository.save(
            Relations(
                name = "rel-$index",
                version = "1.0.0",
                owner = owner,
                notation = notation,
                linkType = linkType,
                createdAt = Instant.now(),
                attrs = """{"group":${index % 2 == 0}}"""
            )
        )
    }

    private fun createRules(
        owner: Users,
        relations: List<Relations>,
        components: List<Components>,
        targetRules: Int
    ) {
        var created = 0
        val attrsPayload = "x".repeat(220)
        loop@ for (from in components.indices) {
            for (to in components.indices) {
                if (from == to) continue
                val relation = relations[created % relations.size]
                relationRulesRepository.save(
                    RelationRules(
                        owner = owner,
                        relation = relation,
                        fromComponent = components[from],
                        toComponent = components[to],
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        attrs = """{"policy":"allow","payload":"$attrsPayload","idx":$created}"""
                    )
                )
                created += 1
                if (created >= targetRules) break@loop
            }
        }
    }
}
