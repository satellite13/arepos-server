package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.time.Instant
import java.util.*
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "models", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "models_name_version_key", columnNames = ["name", "version"])
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class Models(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    val version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @Column(name = "deleted", nullable = false)
    val deleted: Boolean = false
)
