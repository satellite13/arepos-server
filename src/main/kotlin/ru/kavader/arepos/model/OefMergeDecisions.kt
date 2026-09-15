package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
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

/**
 * Persisted duplicate-merge decision for OEF imports: the OEF entity with
 * [oefEntityId] was merged into [targetNode] during validation, so subsequent
 * imports must reuse the target node instead of creating a duplicate.
 */
@Entity
@Table(
    name = "oef_merge_decisions",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(name = "oef_merge_decisions_model_entity_uq", columnNames = ["model", "oef_entity_id"]),
    ],
    indexes = [
        Index(name = "oef_merge_decisions_model_idx", columnList = "model"),
        Index(name = "oef_merge_decisions_target_node_idx", columnList = "target_node"),
    ],
)
class OefMergeDecisions(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model", nullable = false)
    var model: Models,

    @Column(name = "oef_entity_id", nullable = false)
    var oefEntityId: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_node", nullable = false)
    var targetNode: Nodes,

    @Column(name = "signature_type")
    var signatureType: String? = null,

    @Column(name = "signature_name")
    var signatureName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: Users? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,
)
