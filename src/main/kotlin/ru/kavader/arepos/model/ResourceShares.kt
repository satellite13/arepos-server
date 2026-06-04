package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*

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
        Index(
            name = "resource_shares_resource_lookup_idx",
            columnList = "resource_type, resource_id, grantee_user_id, permission"
        ),
        Index(name = "resource_shares_grantee_idx", columnList = "grantee_user_id"),
        Index(name = "resource_shares_granted_by_idx", columnList = "granted_by_user_id")
    ]
)
class ResourceShares(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    var resourceType: ShareResourceType,

    @Column(name = "resource_id", columnDefinition = "uuid", nullable = false)
    var resourceId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grantee_user_id", nullable = true)
    var granteeUser: Users?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", nullable = false)
    var grantedByUser: Users,

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    var permission: SharePermission = SharePermission.EDIT,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
