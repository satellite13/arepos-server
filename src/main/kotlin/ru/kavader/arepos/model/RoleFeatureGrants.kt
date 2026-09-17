package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.Objects

@Entity
@Table(name = "role_feature_grants", schema = "public")
@IdClass(RoleFeatureGrantsId::class)
class RoleFeatureGrants(
    @Id
    @Column(name = "role", nullable = false, length = 32)
    var role: String = "",

    @Id
    @Column(name = "grant_key", nullable = false, length = 128)
    var grantKey: String = "",
)

class RoleFeatureGrantsId(
    var role: String? = null,
    var grantKey: String? = null,
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoleFeatureGrantsId) return false
        return role == other.role && grantKey == other.grantKey
    }

    override fun hashCode(): Int = Objects.hash(role, grantKey)
}
