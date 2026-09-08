package ru.kavader.arepos.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.repository.DiagramCommentAttachmentRepository
import ru.kavader.arepos.repository.DiagramCommentRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.service.FileStorageService
import java.time.Duration
import java.time.Instant

/**
 * Очистка комментариев удалённых диаграмм: строки живут 3 дня после soft-delete
 * диаграммы, затем удаляются вместе с вложениями (FK CASCADE) и объектами S3.
 */
@Component
class CommentRetentionScheduler(
    private val diagramsRepository: DiagramsRepository,
    private val commentRepository: DiagramCommentRepository,
    private val attachmentRepository: DiagramCommentAttachmentRepository,
    private val filesRepository: FilesRepository,
    private val fileStorageProvider: ObjectProvider<FileStorageService>,
    @param:Value($$"${arepos.comments.retention:PT72H}")
    private val retention: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(cron = $$"${arepos.comments.cleanup-cron:0 30 3 * * *}")
    fun purgeCommentsOfDeletedDiagrams() {
        MdcRequestId.withGeneratedIfMissing("comment-retention") {
            val cutoff = Instant.now().minus(retention)
            val expired = diagramsRepository.findDeletedBefore(cutoff)
            if (expired.isEmpty()) return@withGeneratedIfMissing

            var filesPurged = 0
            val fileStorage = fileStorageProvider.ifAvailable
            for (diagramId in expired) {
                val fileIds = attachmentRepository.findFileIdsByDiagramId(diagramId)
                commentRepository.deleteByDiagramId(diagramId)
                if (fileStorage == null) continue
                for (fileId in fileIds) {
                    if (!attachmentRepository.existsByFileId(fileId)) {
                        filesRepository.findById(fileId).ifPresent { file ->
                            fileStorage.deleteObjectQuietly(file.objectKey)
                            filesRepository.delete(file)
                            filesPurged++
                        }
                    }
                }
            }
            logger.info(
                "Purged comments of {} diagram(s) deleted before {}; S3 objects removed: {}",
                expired.size, cutoff, filesPurged
            )
        }
    }
}
