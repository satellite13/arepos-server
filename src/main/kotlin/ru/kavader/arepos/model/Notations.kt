package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "notations", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "notations_name_version_key", columnNames = ["name", "version"])
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class Notations(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    val version: String
)
