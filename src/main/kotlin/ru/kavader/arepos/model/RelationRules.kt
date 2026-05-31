package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import java.time.Instant
import java.util.*
import org.hibernate.type.SqlTypes

@Entity
@DynamicUpdate
@Table(name = "relation_rules", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "relation_rules_relation_from_to_key", columnNames = ["relation", "from_component", "to_component"])
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class RelationRules(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relation", nullable = false)
    var relation: Relations,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_component", nullable = false)
    var fromComponent: Components,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_component", nullable = false)
    var toComponent: Components
)
