package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTreePageProjection
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ModelTreeQueryServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Mock
    lateinit var treePageReader: ModelTreePageReader

    @Test
    fun `checks model ACL outside the database transaction`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val pageable = PageRequest.of(0, 10)
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        doAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            null
        }.`when`(accessService).requireCanViewModel(model)
        `when`(
            treePageReader.readPage(
                model.id!!,
                null,
                excludeSystem = true,
                foldersOnly = false,
                pageable
            )
        ).thenReturn(PageImpl(emptyList(), pageable, 0))

        proxiedService().listChildren(
            modelId = model.id!!,
            parentRef = "root",
            excludeSystem = true,
            foldersOnly = false,
            pageable = pageable
        )
    }

    @Test
    fun `uses one repeatable read snapshot for page rows and total`() {
        val queryServiceTransaction = ModelTreeQueryService::class.java.getAnnotation(Transactional::class.java)
        val readerTransaction = ModelTreePageReader::class.java.getAnnotation(Transactional::class.java)

        assertNull(queryServiceTransaction)
        assertNotNull(readerTransaction)
        assertTrue(readerTransaction.readOnly)
        assertEquals(Isolation.REPEATABLE_READ, readerTransaction.isolation)
    }

    @Test
    fun `maps projected rows while preserving deterministic order`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val projections: Page<NodeTreePageProjection> = PageImpl(
            listOf(
                projection(secondId, "Second", model.id!!, owner.id!!),
                projection(firstId, "First", model.id!!, owner.id!!)
            ),
            pageable,
            2
        )
        `when`(
            nodesRepository.findDirectChildrenPage(
                model.id!!,
                null,
                excludeSystem = true,
                foldersOnly = false,
                pageable
            )
        ).thenReturn(projections)

        val result = reader().readPage(
            modelId = model.id!!,
            parentNodeId = null,
            excludeSystem = true,
            foldersOnly = false,
            pageable = pageable
        )

        assertEquals(listOf(secondId, firstId), result.content.map { it.id })
        assertEquals(2, result.totalElements)
    }

    @Test
    fun `single projection page keeps next page reachable`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 2)
        val projections: Page<NodeTreePageProjection> = PageImpl(
            listOf(
                projection(firstId, "First", model.id!!, owner.id!!),
                projection(secondId, "Second", model.id!!, owner.id!!)
            ),
            pageable,
            3
        )
        `when`(
            nodesRepository.findDirectChildrenPage(
                model.id!!,
                null,
                excludeSystem = true,
                foldersOnly = false,
                pageable
            )
        ).thenReturn(projections)

        val result = reader().readPage(
            modelId = model.id!!,
            parentNodeId = null,
            excludeSystem = true,
            foldersOnly = false,
            pageable = pageable
        )

        assertEquals(listOf(firstId, secondId), result.content.map { it.id })
        assertEquals(3, result.totalElements)
        assertTrue(result.hasNext())
    }

    private fun service() = ModelTreeQueryService(
        modelsRepository,
        nodesRepository,
        accessService,
        ObjectMapper(),
        treePageReader
    )

    private fun reader() = ModelTreePageReader(nodesRepository)

    private fun proxiedService(): ModelTreeQueryService {
        val proxyFactory = ProxyFactory(service())
        proxyFactory.isProxyTargetClass = true
        val transactionInterceptor = TransactionInterceptor()
        transactionInterceptor.transactionManager = TrackingTransactionManager()
        transactionInterceptor.transactionAttributeSource = AnnotationTransactionAttributeSource()
        proxyFactory.addAdvice(transactionInterceptor)
        return proxyFactory.proxy as ModelTreeQueryService
    }

    private fun projection(
        id: UUID,
        name: String,
        modelId: UUID,
        ownerId: UUID
    ) = object : NodeTreePageProjection {
        override fun getId(): UUID = id
        override fun getStableId(): UUID = id
        override fun getName(): String = name
        override fun getModelId(): UUID = modelId
        override fun getOwnerId(): UUID = ownerId
        override fun getNodeTypeId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        override fun getParentNodeId(): UUID? = null
        override fun getAttrs(): String? = null
        override fun getCreatedAt(): Instant? = null
        override fun getUpdatedAt(): Instant? = null
        override fun getHasChildren(): Boolean = false
    }

    private class TrackingTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
