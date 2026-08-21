package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ModelResolveControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes

    @BeforeEach
    fun setUp() {
        owner = saveUser(Role.USER)
        model = saveModel(owner)
        nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "resolve-node-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        linkType = linkTypesRepository.save(
            LinkTypes(
                name = "resolve-link-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
    }

    @Test
    fun `resolves ordered unique nodes and reports model-scoped missing ids`() {
        val first = saveNode(model, "first")
        val second = saveNode(model, "second")
        val foreignModel = saveModel(owner)
        val foreign = saveNode(foreignModel, "foreign")
        val absent = UUID.randomUUID()

        val response = resolve(
            "/api/v1/models/${model.id}/nodes:resolve",
            mapOf("nodeIds" to listOf(second.id, first.id, second.id, absent, foreign.id)),
            owner
        )
            .andExpect(status().isOk)
            .andReturn()

        val json = objectMapper.readTree(response.response.contentAsString)
        assertEquals(
            listOf(second.id.toString(), first.id.toString()),
            json.path("nodes").map { it.path("id").asText() }
        )
        assertEquals(
            listOf(absent.toString(), foreign.id.toString()),
            json.path("missingIds").map { it.asText() }
        )
    }

    @Test
    fun `resolves explicit and endpoint links in deterministic deduplicated order`() {
        val source = saveNode(model, "source")
        val middle = saveNode(model, "middle")
        val target = saveNode(model, "target")
        val explicit = saveLink(model, middle, target)
        val endpointLinks = listOf(
            saveLink(model, source, middle),
            saveLink(model, target, source)
        )
        val foreignModel = saveModel(owner)
        val foreignSource = saveNode(foreignModel, "foreign-source")
        val foreignTarget = saveNode(foreignModel, "foreign-target")
        val foreignLink = saveLink(foreignModel, foreignSource, foreignTarget)
        val absent = UUID.randomUUID()

        val response = resolve(
            "/api/v1/models/${model.id}/links:resolve",
            mapOf(
                "linkIds" to listOf(explicit.id, explicit.id, absent, foreignLink.id),
                "endpointNodeIds" to listOf(source.id, source.id, foreignSource.id)
            ),
            owner
        )
            .andExpect(status().isOk)
            .andReturn()

        val json = objectMapper.readTree(response.response.contentAsString)
        val expectedIds = listOf(explicit.id!!) +
            endpointLinks.map { it.id!! }.sortedBy(UUID::toString)
        assertEquals(expectedIds.map(UUID::toString), json.path("links").map { it.path("id").asText() })
        assertEquals(
            listOf(absent.toString(), foreignLink.id.toString()),
            json.path("missingLinkIds").map { it.asText() }
        )
    }

    @Test
    fun `endpoint-only link resolve has no missing explicit ids`() {
        val absentEndpoint = UUID.randomUUID()

        resolve(
            "/api/v1/models/${model.id}/links:resolve",
            mapOf("endpointNodeIds" to listOf(absentEndpoint)),
            owner
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.links.length()").value(0))
            .andExpect(jsonPath("$.missingLinkIds.length()").value(0))
    }

    @Test
    fun `rejects empty and oversized resolve requests`() {
        resolve("/api/v1/models/${model.id}/nodes:resolve", mapOf("nodeIds" to emptyList<UUID>()), owner)
            .andExpect(status().isBadRequest)
        resolve("/api/v1/models/${model.id}/links:resolve", emptyMap<String, List<UUID>>(), owner)
            .andExpect(status().isBadRequest)

        val oversized = List(2001) { UUID.randomUUID() }
        resolve("/api/v1/models/${model.id}/nodes:resolve", mapOf("nodeIds" to oversized), owner)
            .andExpect(status().isBadRequest)
        resolve(
            "/api/v1/models/${model.id}/links:resolve",
            mapOf("linkIds" to oversized),
            owner
        )
            .andExpect(status().isBadRequest)
        resolve(
            "/api/v1/models/${model.id}/links:resolve",
            mapOf("endpointNodeIds" to oversized),
            owner
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `allows owner shared viewer and admin but rejects stranger before resolve data`() {
        val node = saveNode(model, "visible")
        val viewer = saveUser(Role.USER)
        val admin = saveUser(Role.ADMIN)
        val stranger = saveUser(Role.USER)
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )
        val payload = mapOf("nodeIds" to listOf(node.id))

        listOf(owner to Role.USER, viewer to Role.USER, admin to Role.ADMIN).forEach { (user, role) ->
            resolve("/api/v1/models/${model.id}/nodes:resolve", payload, user, role)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nodes[0].id").value(node.id.toString()))
        }
        resolve("/api/v1/models/${model.id}/nodes:resolve", payload, stranger, Role.USER)
            .andExpect(status().isForbidden)
    }

    private fun resolve(
        path: String,
        payload: Any,
        user: Users,
        role: Role = user.role
    ) = mockMvc.perform(
        post(path)
            .withAuth(user.id!!, role)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload))
    )

    private fun saveUser(role: Role): Users = usersRepository.save(
        Users(
            email = "resolve-${UUID.randomUUID()}@test.com",
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun saveModel(modelOwner: Users): Models = modelsRepository.save(
        Models(
            name = "resolve-model-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = modelOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNode(targetModel: Models, name: String): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = nodeType,
            createdAt = Instant.now()
        )
    )

    private fun saveLink(targetModel: Models, source: Nodes, target: Nodes): Links = linksRepository.save(
        Links(
            stableId = UUID.randomUUID(),
            model = targetModel,
            owner = targetModel.owner,
            linkType = linkType,
            source = source,
            target = target,
            createdAt = Instant.now()
        )
    )
}
