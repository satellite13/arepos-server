package ru.kavader.arepos.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import ru.kavader.arepos.dto.model.BatchConflictItem
import ru.kavader.arepos.dto.model.BatchSaveConflictException
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.modelbatch.BatchConflictCollector
import ru.kavader.arepos.service.modelbatch.BatchEventBuilder
import ru.kavader.arepos.service.modelbatch.BatchGraphOpsExecutor
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class ModelBatchSaveServiceTest {

    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Mock
    lateinit var batchConflictCollector: BatchConflictCollector

    @Mock
    lateinit var batchGraphOpsExecutor: BatchGraphOpsExecutor

    @Mock
    lateinit var batchEventBuilder: BatchEventBuilder

    @Mock
    lateinit var modelSyncBroadcaster: ModelSyncBroadcaster

    @Test
    fun `batch save throws conflict before applying graph operations`() {
        val registry = SimpleMeterRegistry()
        val metrics = CustomMetricsService(registry)
        val service = ModelBatchSaveService(
            modelsRepository,
            usersRepository,
            accessService,
            metrics,
            batchConflictCollector,
            batchGraphOpsExecutor,
            batchEventBuilder,
            modelSyncBroadcaster
        )
        val owner = Users(id = UUID.randomUUID(), email = "batch-owner@test.com")
        val model = Models(
            id = UUID.randomUUID(),
            name = "batch-model",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.now()
        )
        val request = BatchSaveRequest()
        val conflict = BatchConflictItem(
            kind = "node",
            id = UUID.randomUUID(),
            serverUpdatedAt = Instant.parse("2026-01-01T00:00:01Z"),
            clientBaseUpdatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        `when`(batchConflictCollector.collect(request, model)).thenReturn(listOf(conflict))

        val exception = assertFailsWith<BatchSaveConflictException> {
            service.batchSave(model.id!!, request)
        }

        assertEquals(listOf(conflict), exception.conflicts)
        verifyNoInteractions(batchGraphOpsExecutor)
        assertEquals(
            1L,
            registry.find("arepos_batch_save_duration")
                .tag("outcome", "conflict")
                .timer()
                ?.count()
        )
    }
}
