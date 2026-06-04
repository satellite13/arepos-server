package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@DynamicUpdate
@Table(
    name = "models",
    schema = "public",
    indexes = [
        Index(name = "models_owner_idx", columnList = "owner"),
        Index(name = "models_source_id_idx", columnList = "source_id")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Models(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    var version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    var source: Models? = null,

    @Column(name = "deleted", nullable = false)
    var deleted: Boolean = false,

    @Column(name = "sync_revision", nullable = false)
    var syncRevision: Long = 0
)
