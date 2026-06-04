package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

enum class Role {
    USER, EDITOR, ADMIN
}

@Entity
@DynamicUpdate
@Table(
    name = "users", schema = "public", indexes = [
        Index(name = "users_attrs_idx", columnList = "attrs", unique = false)
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Users(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "email_type")
    var email: String,

    @JsonIgnore
    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: Role = Role.USER,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
