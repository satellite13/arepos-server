package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "model_sync_outbox",
    schema = "public",
    indexes = [
        Index(name = "model_sync_outbox_model_id_idx", columnList = "model_id")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class ModelSyncOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    var model: Models,

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    var payload: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null
)
