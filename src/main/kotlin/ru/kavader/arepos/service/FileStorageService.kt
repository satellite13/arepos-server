package ru.kavader.arepos.service

import io.minio.*
import io.minio.messages.VersioningConfiguration
import io.minio.messages.Version
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kavader.arepos.config.MinioProperties
import ru.kavader.arepos.model.FileVersions
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.FileVersionsRepository
import ru.kavader.arepos.repository.FilesRepository
import java.io.ByteArrayInputStream
import java.util.*

@Service
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
@EnableConfigurationProperties(MinioProperties::class)
class FileStorageService(
    private val minioClient: MinioClient,
    private val minioProperties: MinioProperties,
    private val filesRepository: FilesRepository,
    private val fileVersionsRepository: FileVersionsRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(FileStorageService::class.java)
        private const val MAX_SIZE: Long = 5 * 1024 * 1024 // 5 MB
        private val ALLOWED_IMAGE_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml"
        )
        private const val MARKDOWN_TYPE = "text/markdown"
    }

    @EventListener(ContextRefreshedEvent::class)
    fun ensureBucketExists() {
        try {
            val exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioProperties.bucket).build()
            )
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(minioProperties.bucket).build()
                )
                log.info("Created MinIO bucket: {}", minioProperties.bucket)
            }
            // Enable versioning on bucket
            enableVersioning()
        } catch (e: Exception) {
            log.error("Failed to ensure MinIO bucket exists: {}", e.message)
            throw e
        }
    }

    private fun enableVersioning() {
        try {
            val config = VersioningConfiguration(
                VersioningConfiguration.Status.ENABLED,
                null
            )
            minioClient.setBucketVersioning(
                SetBucketVersioningArgs.builder()
                    .bucket(minioProperties.bucket)
                    .config(config)
                    .build()
            )
            log.info("Enabled versioning for bucket: {}", minioProperties.bucket)
        } catch (e: Exception) {
            log.warn("Could not enable versioning (may already be enabled): {}", e.message)
        }
    }

    fun upload(file: MultipartFile, owner: Users): Files {
        require(file.size <= MAX_SIZE) { "File size exceeds 5 MB limit" }
        val contentType = file.contentType ?: "application/octet-stream"
        require(isAllowedType(contentType)) {
            "File type not allowed: $contentType. Allowed: $ALLOWED_IMAGE_TYPES, $MARKDOWN_TYPE"
        }
        
        val fileId = UUID.randomUUID()
        val objectKey = "uploads/${owner.id!!}/$fileId/${sanitizeFilename(file.originalFilename ?: "file")}"

        val result = minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectKey)
                .stream(file.inputStream, file.size, -1)
                .contentType(contentType)
                .build()
        )

        val entity = Files(
            id = fileId,
            owner = owner,
            filename = file.originalFilename ?: "file",
            contentType = contentType,
            size = file.size,
            objectKey = objectKey,
            createdAt = java.time.Instant.now()
        )
        val saved = filesRepository.save(entity)
        
        // Save version info
        saveVersion(saved, result.versionId(), owner, file.size)
        
        return saved
    }

    fun uploadMarkdown(content: String, filename: String, owner: Users): Files {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_SIZE) { "Markdown content exceeds 5 MB limit" }
        
        val fileId = UUID.randomUUID()
        val safeFilename = sanitizeFilename(filename).let {
            if (it.endsWith(".md")) it else "$it.md"
        }
        val objectKey = "markdown/${owner.id!!}/$fileId/$safeFilename"

        val result = minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectKey)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(MARKDOWN_TYPE)
                .build()
        )

        val entity = Files(
            id = fileId,
            owner = owner,
            filename = safeFilename,
            contentType = MARKDOWN_TYPE,
            size = bytes.size.toLong(),
            objectKey = objectKey,
            createdAt = java.time.Instant.now()
        )
        val saved = filesRepository.save(entity)
        
        // Save version info
        saveVersion(saved, result.versionId(), owner, bytes.size.toLong())
        
        return saved
    }

    fun getFile(id: UUID): Pair<Files, Resource>? {
        val file = filesRepository.findById(id).orElse(null) ?: return null
        val latestVersion = fileVersionsRepository.findTopByFileOrderByVersionNumberDesc(file)
        val builder = GetObjectArgs.builder()
            .bucket(minioProperties.bucket)
            .`object`(file.objectKey)
        if (latestVersion != null && latestVersion.versionId != "null") {
            builder.versionId(latestVersion.versionId)
        }
        val stream = minioClient.getObject(builder.build())
        return file to InputStreamResource(stream)
    }

    fun getFileVersion(id: UUID, versionNumber: Int): Pair<Files, Resource>? {
        val file = filesRepository.findById(id).orElse(null) ?: return null
        val version = fileVersionsRepository.findByFileAndVersionNumber(file, versionNumber) ?: return null
        
        val stream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(file.objectKey)
                .versionId(version.versionId)
                .build()
        )
        return file to InputStreamResource(stream)
    }

    fun listVersions(id: UUID): List<FileVersionInfo> {
        val file = filesRepository.findById(id).orElse(null) ?: return emptyList()
        return fileVersionsRepository.findByFileOrderByVersionNumberDesc(file).map { version ->
            FileVersionInfo(
                versionNumber = version.versionNumber,
                createdAt = version.createdAt!!,
                createdBy = version.createdBy.id!!,
                size = version.size
            )
        }
    }

    fun updateMarkdown(id: UUID, content: String, owner: Users): Files {
        val file = filesRepository.findById(id).orElse(null) 
            ?: throw IllegalArgumentException("File not found: $id")
        
        require(file.contentType == MARKDOWN_TYPE) { "File is not a markdown file" }
        
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_SIZE) { "Markdown content exceeds 5 MB limit" }

        val result = minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(file.objectKey)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(MARKDOWN_TYPE)
                .build()
        )

        // Save new version
        saveVersion(file, result.versionId(), owner, bytes.size.toLong())
        
        // Update file record with new size
        val updatedFile = file.copy(
            size = bytes.size.toLong(),
            createdAt = java.time.Instant.now()
        )
        return filesRepository.save(updatedFile)
    }

    private fun saveVersion(file: Files, versionId: String?, owner: Users, size: Long) {
        val lastVersion = fileVersionsRepository.findTopByFileOrderByVersionNumberDesc(file)
        val nextVersionNumber = (lastVersion?.versionNumber ?: 0) + 1
        
        val version = FileVersions(
            file = file,
            versionId = versionId ?: "null",
            versionNumber = nextVersionNumber,
            createdAt = java.time.Instant.now(),
            createdBy = owner,
            size = size
        )
        fileVersionsRepository.save(version)
    }

    private fun isAllowedType(contentType: String): Boolean {
        return ALLOWED_IMAGE_TYPES.any { contentType.startsWith(it, ignoreCase = true) || contentType == it }
                || contentType == MARKDOWN_TYPE
                || contentType.startsWith("text/")
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(200)

    data class FileVersionInfo(
        val versionNumber: Int,
        val createdAt: java.time.Instant,
        val createdBy: UUID,
        val size: Long
    )
}
