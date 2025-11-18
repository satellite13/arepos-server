package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "relation_rules", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "relation_rules_relation_from_to_key", columnNames = ["relation", "from_component", "to_component"])
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class RelationRules(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relation", nullable = false)
    val relation: Relations,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_component", nullable = false)
    val fromComponent: Components,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_component", nullable = false)
    val toComponent: Components
)
