package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.Objects
import java.util.UUID

@Entity
@Table(name = "user_feature_grant_allows", schema = "public")
@IdClass(UserFeatureGrantAllowsId::class)
class UserFeatureGrantAllows(
    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID(0, 0),

    @Id
    @Column(name = "grant_key", nullable = false, length = 128)
    var grantKey: String = "",
)

class UserFeatureGrantAllowsId(
    var userId: UUID? = null,
    var grantKey: String? = null,
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserFeatureGrantAllowsId) return false
        return userId == other.userId && grantKey == other.grantKey
    }

    override fun hashCode(): Int = Objects.hash(userId, grantKey)
}
