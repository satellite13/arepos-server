package ru.kavader.arepos.service

import io.minio.*
import io.minio.errors.MinioException
import io.minio.messages.VersioningConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.MinioProperties
import ru.kavader.arepos.model.FileVersions
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.FileVersionsRepository
import ru.kavader.arepos.repository.FilesRepository
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
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
        private const val VERSION_ID_NULL_SENTINEL = "null"
        private val ALLOWED_IMAGE_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
        )
        private const val MARKDOWN_TYPE = "text/markdown"
        private const val SITE_ASSET_MAX_SIZE: Long = 20 * 1024 * 1024
        /** Keep download/object keys ASCII-safe while preserving readable Russian names. */
        private val CYRILLIC_TO_LATIN = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D",
            'Е' to "E", 'Ё' to "E", 'Ж' to "Zh", 'З' to "Z", 'И' to "I",
            'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
            'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
            'У' to "U", 'Ф' to "F", 'Х' to "H", 'Ц' to "Ts", 'Ч' to "Ch",
            'Ш' to "Sh", 'Щ' to "Sch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "",
            'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
        )
        private val SITE_ASSET_TYPES = setOf(
            "application/json",
            "application/zip",
            "application/octet-stream",
            "text/plain"
        )
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
        } catch (e: MinioException) {
            log.error("Failed to ensure MinIO bucket exists", e)
            throw e
        } catch (e: IOException) {
            log.error("I/O failure while ensuring MinIO bucket exists", e)
            throw e
        } catch (e: GeneralSecurityException) {
            log.error("Security failure while ensuring MinIO bucket exists", e)
            throw e
        } catch (e: RuntimeException) {
            log.error("Unexpected failure while ensuring MinIO bucket exists", e)
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
        } catch (e: MinioException) {
            log.warn("Could not enable MinIO bucket versioning", e)
        } catch (e: IOException) {
            log.warn("I/O failure while enabling MinIO bucket versioning", e)
        } catch (e: GeneralSecurityException) {
            log.warn("Security failure while enabling MinIO bucket versioning", e)
        } catch (e: RuntimeException) {
            log.warn("Unexpected failure while enabling MinIO bucket versioning", e)
        }
    }

    fun upload(file: MultipartFile, owner: Users): Files {
        if (file.size > MAX_SIZE) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File size exceeds 5 MB limit")
        val contentType = file.contentType ?: "application/octet-stream"
        if (!isAllowedType(contentType)) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "File type not allowed: $contentType. Allowed: $ALLOWED_IMAGE_TYPES, $MARKDOWN_TYPE"
        )

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

    fun uploadSiteAsset(file: MultipartFile, owner: Users): Files {
        if (file.size > SITE_ASSET_MAX_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File size exceeds 20 MB limit")
        }
        val contentType = file.contentType ?: "application/octet-stream"
        val allowed = SITE_ASSET_TYPES.any { contentType.equals(it, ignoreCase = true) } ||
            contentType.startsWith("application/json", ignoreCase = true) ||
            contentType.startsWith("text/", ignoreCase = true)
        if (!allowed) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File type not allowed for site assets: $contentType"
            )
        }

        val fileId = UUID.randomUUID()
        val filename = sanitizeFilename(file.originalFilename ?: "asset.json")
        val objectKey = "site-downloads/${owner.id!!}/$fileId/$filename"

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
            filename = filename,
            contentType = contentType,
            size = file.size,
            objectKey = objectKey,
            createdAt = java.time.Instant.now()
        )
        val saved = filesRepository.save(entity)
        saveVersion(saved, result.versionId(), owner, file.size)
        return saved
    }

    fun uploadMarkdown(content: String, filename: String, owner: Users): Files {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_SIZE) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Markdown content exceeds 5 MB limit"
        )

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
        if (latestVersion != null && isUsableVersionId(latestVersion.versionId)) {
            builder.versionId(latestVersion.versionId)
        }
        val stream = minioClient.getObject(builder.build())
        return file to InputStreamResource(stream)
    }

    fun getFileMetadata(id: UUID): Files? = filesRepository.findById(id).orElse(null)

    fun getFileVersion(id: UUID, versionNumber: Int): Pair<Files, Resource>? {
        val file = filesRepository.findById(id).orElse(null) ?: return null
        val version = fileVersionsRepository.findByFileAndVersionNumber(file, versionNumber) ?: return null

        val builder = GetObjectArgs.builder()
            .bucket(minioProperties.bucket)
            .`object`(file.objectKey)
        if (isUsableVersionId(version.versionId)) {
            builder.versionId(version.versionId)
        }
        val stream = minioClient.getObject(builder.build())
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
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $id")

        if (file.contentType != MARKDOWN_TYPE) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "File is not a markdown file"
        )

        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_SIZE) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Markdown content exceeds 5 MB limit"
        )

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
        file.size = bytes.size.toLong()
        file.createdAt = java.time.Instant.now()
        return filesRepository.save(file)
    }

    private fun saveVersion(file: Files, versionId: String?, owner: Users, size: Long) {
        val lastVersion = fileVersionsRepository.findTopByFileOrderByVersionNumberDesc(file)
        val nextVersionNumber = (lastVersion?.versionNumber ?: 0) + 1

        val version = FileVersions(
            file = file,
            versionId = versionId ?: VERSION_ID_NULL_SENTINEL,
            versionNumber = nextVersionNumber,
            createdAt = java.time.Instant.now(),
            createdBy = owner,
            size = size
        )
        fileVersionsRepository.save(version)
    }

    private fun isUsableVersionId(versionId: String?): Boolean =
        !versionId.isNullOrBlank() && !versionId.equals(VERSION_ID_NULL_SENTINEL, ignoreCase = true)

    private fun isAllowedType(contentType: String): Boolean {
        return ALLOWED_IMAGE_TYPES.any { contentType.startsWith(it, ignoreCase = true) || contentType == it }
                || contentType == MARKDOWN_TYPE
                || contentType.startsWith("text/")
    }

    private fun sanitizeFilename(name: String): String {
        val transliterated = transliterateCyrillicToLatin(name.trim().ifEmpty { "file" })
        return transliterated
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "file" }
            .take(200)
    }

    private fun transliterateCyrillicToLatin(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            out.append(CYRILLIC_TO_LATIN[ch] ?: ch)
        }
        return out.toString()
    }

    data class FileVersionInfo(
        val versionNumber: Int,
        val createdAt: java.time.Instant,
        val createdBy: UUID,
        val size: Long
    )
}
