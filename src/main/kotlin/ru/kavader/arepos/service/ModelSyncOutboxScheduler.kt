package ru.kavader.arepos.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.kavader.arepos.config.MdcRequestId

@Component
class ModelSyncOutboxScheduler(
    private val publishService: ModelSyncOutboxPublishService,
    @param:Value($$"${arepos.model-sync.outbox-enabled:false}") private val outboxEnabled: Boolean
) {

    @Scheduled(fixedDelayString = $$"${arepos.model-sync.outbox-publish-interval-ms:500}")
    fun publishOutbox() {
        MdcRequestId.withGeneratedIfMissing("outbox-scheduler") {
            if (!outboxEnabled) {
                return@withGeneratedIfMissing
            }
            publishService.publishPending()
        }
    }
}
