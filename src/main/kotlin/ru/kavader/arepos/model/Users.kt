package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "users", schema = "public", indexes = [
        Index(name = "users_attrs_idx", columnList = "attrs", unique = false)
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Users(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "email_type")
    val email: String,

    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null
)
