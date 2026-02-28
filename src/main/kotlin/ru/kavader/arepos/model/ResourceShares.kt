package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

enum class ShareResourceType {
    MODEL,
    NOTATION,
    NODE_TYPE,
    LINK_TYPE,
    NODE_SHAPE
}

enum class SharePermission {
    VIEW,
    EDIT
}

@Entity
@Table(
    name = "resource_shares",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(
            name = "resource_shares_unique_share",
            columnNames = ["resource_type", "resource_id", "grantee_user_id", "permission"]
        )
    ],
    indexes = [
        Index(name = "resource_shares_resource_lookup_idx", columnList = "resource_type, resource_id, grantee_user_id, permission"),
        Index(name = "resource_shares_grantee_idx", columnList = "grantee_user_id"),
        Index(name = "resource_shares_granted_by_idx", columnList = "granted_by_user_id")
    ]
)
data class ResourceShares(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    val resourceType: ShareResourceType,

    @Column(name = "resource_id", columnDefinition = "uuid", nullable = false)
    val resourceId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grantee_user_id", nullable = false)
    val granteeUser: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", nullable = false)
    val grantedByUser: Users,

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    val permission: SharePermission = SharePermission.EDIT,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null
)
