package ru.kavader.arepos.service

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import io.minio.ObjectWriteResponse
import io.minio.PutObjectArgs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import ru.kavader.arepos.config.MinioProperties
import ru.kavader.arepos.model.FileVersions
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.FileVersionsRepository
import ru.kavader.arepos.repository.FilesRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class, OutputCaptureExtension::class)
class FileStorageServiceTest {

    @Mock
    lateinit var minioClient: MinioClient

    @Mock
    lateinit var filesRepository: FilesRepository

    @Mock
    lateinit var fileVersionsRepository: FileVersionsRepository

    @Test
    fun `upload markdown stores metadata and first version`() {
        val service = service()
        val owner = Users(id = UUID.randomUUID(), email = "storage-owner@test.com")
        val response = org.mockito.Mockito.mock(ObjectWriteResponse::class.java)
        `when`(response.versionId()).thenReturn("minio-v1")
        `when`(minioClient.putObject(any(PutObjectArgs::class.java))).thenReturn(response)
        `when`(filesRepository.save(any(Files::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(fileVersionsRepository.save(any(FileVersions::class.java)))
            .thenAnswer { it.getArgument(0) }

        val saved = service.uploadMarkdown("# report", "audit report", owner)

        assertEquals("audit_report.md", saved.filename)
        assertEquals("text/markdown", saved.contentType)
        assertEquals("# report".toByteArray().size.toLong(), saved.size)
        val versionCaptor = ArgumentCaptor.forClass(FileVersions::class.java)
        org.mockito.Mockito.verify(fileVersionsRepository).save(versionCaptor.capture())
        assertEquals(1, versionCaptor.value.versionNumber)
        assertEquals("minio-v1", versionCaptor.value.versionId)
        assertEquals(owner.id, versionCaptor.value.createdBy.id)
    }

    @Test
    fun `upload markdown transliterates cyrillic filenames to ascii`() {
        val service = service()
        val owner = Users(id = UUID.randomUUID(), email = "storage-owner@test.com")
        val response = org.mockito.Mockito.mock(ObjectWriteResponse::class.java)
        `when`(response.versionId()).thenReturn("minio-v1")
        `when`(minioClient.putObject(any(PutObjectArgs::class.java))).thenReturn(response)
        `when`(filesRepository.save(any(Files::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(fileVersionsRepository.save(any(FileVersions::class.java)))
            .thenAnswer { it.getArgument(0) }

        val saved = service.uploadMarkdown("# c4", "С4 композиция", owner)

        assertEquals("S4_kompozitsiya.md", saved.filename)
    }

    @Test
    fun `bucket initialization logs and rethrows storage exception`(output: CapturedOutput) {
        val service = service()
        `when`(minioClient.bucketExists(any(BucketExistsArgs::class.java)))
            .thenThrow(IllegalStateException("minio unavailable"))

        val exception = assertFailsWith<IllegalStateException> {
            service.ensureBucketExists()
        }

        assertEquals("minio unavailable", exception.message)
        assertTrue(output.out.contains("Failed to ensure MinIO bucket exists"))
        assertTrue(output.out.contains("minio unavailable"))
    }

    private fun service(): FileStorageService =
        FileStorageService(
            minioClient,
            MinioProperties(bucket = "test-bucket", endpoint = "http://localhost:9000"),
            filesRepository,
            fileVersionsRepository
        )
}
