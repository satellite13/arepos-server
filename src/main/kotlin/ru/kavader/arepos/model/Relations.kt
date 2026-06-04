package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@DynamicUpdate
@BatchSize(size = 50)
@Table(
    name = "relations", schema = "public", uniqueConstraints = [
        UniqueConstraint(name = "relations_notation_name_version_key", columnNames = ["notation", "name", "version"])
    ], indexes = [
        Index(name = "relations_owner_idx", columnList = "owner"),
        Index(name = "relations_notation_idx", columnList = "notation"),
        Index(name = "relations_link_type_idx", columnList = "link_type")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Relations(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    override var attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    override var version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    override var owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notation", nullable = false)
    override var notation: Notations,

    @Column(name = "name", nullable = false)
    override var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_type", nullable = false)
    var linkType: LinkTypes
) : NotationBoundEntity
