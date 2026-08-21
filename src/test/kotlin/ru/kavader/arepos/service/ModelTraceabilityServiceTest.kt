package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.aop.framework.ProxyFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.kavader.arepos.dto.model.DiagramReferenceResponse
import ru.kavader.arepos.dto.model.GraphDirection
import ru.kavader.arepos.dto.model.GraphNeighborResponse
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
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
class ModelTraceabilityServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Mock
    lateinit var traceabilityReader: ModelTraceabilityReader

    @Test
    fun `checks model ACL outside transaction before graph reader`() {
        val model = model()
        val nodeId = UUID.randomUUID()
        val expected = Page.empty<GraphNeighborResponse>()
        `when`(modelsRepository.findById(MODEL_ID)).thenReturn(Optional.of(model))
        doAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            null
        }.`when`(accessService).requireCanViewModel(model)
        `when`(
            traceabilityReader.graphNeighbors(
                MODEL_ID,
                nodeId,
                GraphDirection.BOTH,
                null,
                Pageable.ofSize(50)
            )
        ).thenReturn(expected)

        val result = proxiedService().graphNeighbors(
            MODEL_ID,
            nodeId,
            GraphDirection.BOTH,
            null,
            Pageable.ofSize(50)
        )

        assertEquals(expected, result)
    }

    @Test
    fun `checks model ACL before diagram reference reader`() {
        val model = model()
        val nodeId = UUID.randomUUID()
        val expected = Page.empty<DiagramReferenceResponse>()
        `when`(modelsRepository.findById(MODEL_ID)).thenReturn(Optional.of(model))
        doAnswer {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            null
        }.`when`(accessService).requireCanViewModel(model)
        `when`(
            traceabilityReader.diagramReferences(MODEL_ID, nodeId, Pageable.ofSize(50))
        ).thenReturn(expected)

        val result = proxiedService().diagramReferences(MODEL_ID, nodeId, Pageable.ofSize(50))

        assertEquals(expected, result)
    }

    @Test
    fun `keeps transaction on database reader only`() {
        assertNull(ModelTraceabilityService::class.java.getAnnotation(Transactional::class.java))
        val readerTransaction = ModelTraceabilityReader::class.java.getAnnotation(Transactional::class.java)
        assertNotNull(readerTransaction)
        assertTrue(readerTransaction.readOnly)
    }

    private fun service() = ModelTraceabilityService(modelsRepository, accessService, traceabilityReader)

    private fun proxiedService(): ModelTraceabilityService {
        val proxyFactory = ProxyFactory(service())
        proxyFactory.isProxyTargetClass = true
        val transactionInterceptor = TransactionInterceptor()
        transactionInterceptor.transactionManager = TrackingTransactionManager()
        transactionInterceptor.transactionAttributeSource = AnnotationTransactionAttributeSource()
        proxyFactory.addAdvice(transactionInterceptor)
        return proxyFactory.proxy as ModelTraceabilityService
    }

    private fun model(): Models {
        val owner = Users(id = UUID.randomUUID(), email = "trace-owner@test.com", createdAt = Instant.now())
        return Models(
            id = MODEL_ID,
            name = "trace-model",
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
