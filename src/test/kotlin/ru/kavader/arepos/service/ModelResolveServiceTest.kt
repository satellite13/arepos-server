package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.domain.Pageable
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.MODEL_LINK_RESOLVE_MAX_RESULTS
import ru.kavader.arepos.dto.model.ModelLinksResolveRequest
import ru.kavader.arepos.dto.model.ModelLinksResolveResponse
import ru.kavader.arepos.dto.model.ModelNodesResolveRequest
import ru.kavader.arepos.dto.model.ModelNodesResolveResponse
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
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
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class ModelResolveServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var linksRepository: LinksRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Mock
    lateinit var modelMapper: ModelMapper

    @Mock
    lateinit var resolveReader: ModelResolveReader

    @Test
    fun `checks model ACL outside the database transaction`() {
        val model = model()
        val request = ModelNodesResolveRequest(listOf(UUID.randomUUID()))
        val expected = ModelNodesResolveResponse(emptyList(), request.nodeIds)
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        doAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            null
        }.`when`(accessService).requireCanViewModel(model)
        `when`(resolveReader.resolveNodes(model.id!!, request)).thenReturn(expected)

        val result = proxiedService().resolveNodes(model.id!!, request)

        assertEquals(expected, result)
    }

    @Test
    fun `keeps transactions on the proxied database reader only`() {
        assertNull(ModelResolveService::class.java.getAnnotation(Transactional::class.java))
        val readerTransaction = ModelResolveReader::class.java.getAnnotation(Transactional::class.java)
        assertNotNull(readerTransaction)
        assertTrue(readerTransaction.readOnly)
    }

    @Test
    fun `rejects endpoint result overflow before loading link entities`() {
        val endpointId = UUID.randomUUID()
        val ids = List(MODEL_LINK_RESOLVE_MAX_RESULTS + 1) { UUID.randomUUID() }
        `when`(
            linksRepository.findIdsByModelIdAndEndpointNodeIds(
                modelId = MODEL_ID,
                endpointNodeIds = listOf(endpointId),
                pageable = Pageable.ofSize(MODEL_LINK_RESOLVE_MAX_RESULTS + 1)
            )
        ).thenReturn(ids)

        val error = assertFailsWith<ResponseStatusException> {
            reader().resolveLinks(
                MODEL_ID,
                ModelLinksResolveRequest(endpointNodeIds = listOf(endpointId))
            )
        }

        assertEquals(413, error.statusCode.value())
        assertEquals("MODEL_LINK_RESOLVE_RESULT_LIMIT_EXCEEDED", error.reason)
        assertTrue(
            mockingDetails(linksRepository).invocations.none {
                it.method.name == "findByModel_IdAndIdIn"
            }
        )
        verifyNoInteractions(modelMapper)
    }

    @Test
    fun `accepts endpoint result exactly at the server boundary`() {
        val endpointId = UUID.randomUUID()
        val ids = List(MODEL_LINK_RESOLVE_MAX_RESULTS) { UUID.randomUUID() }
        `when`(
            linksRepository.findIdsByModelIdAndEndpointNodeIds(
                modelId = MODEL_ID,
                endpointNodeIds = listOf(endpointId),
                pageable = Pageable.ofSize(MODEL_LINK_RESOLVE_MAX_RESULTS + 1)
            )
        ).thenReturn(ids)
        `when`(linksRepository.findByModel_IdAndIdIn(MODEL_ID, ids)).thenReturn(emptyList())

        val result = reader().resolveLinks(
            MODEL_ID,
            ModelLinksResolveRequest(endpointNodeIds = listOf(endpointId))
        )

        assertEquals(ModelLinksResolveResponse(emptyList(), emptyList()), result)
        verify(linksRepository).findByModel_IdAndIdIn(MODEL_ID, ids)
    }

    private fun service() = ModelResolveService(modelsRepository, accessService, resolveReader)

    private fun reader() = ModelResolveReader(nodesRepository, linksRepository, modelMapper)

    private fun proxiedService(): ModelResolveService {
        val proxyFactory = ProxyFactory(service())
        proxyFactory.isProxyTargetClass = true
        val transactionInterceptor = TransactionInterceptor()
        transactionInterceptor.transactionManager = TrackingTransactionManager()
        transactionInterceptor.transactionAttributeSource = AnnotationTransactionAttributeSource()
        proxyFactory.addAdvice(transactionInterceptor)
        return proxyFactory.proxy as ModelResolveService
    }

    private fun model(): Models {
        val owner = Users(id = UUID.randomUUID(), email = "resolve-owner@test.com", createdAt = Instant.now())
        return Models(
            id = MODEL_ID,
            name = "resolve-model",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.now()
        )
    }

    private class TrackingTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private companion object {
        val MODEL_ID: UUID = UUID.randomUUID()
    }
}
