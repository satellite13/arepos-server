package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "model_validation_locks", schema = "public")
class ModelValidationLocks(
    @Id
    @Column(name = "model", columnDefinition = "uuid", updatable = false, nullable = false)
    var modelId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locked_by", nullable = false)
    var lockedBy: Users,

    @Column(name = "locked_at", nullable = false)
    var lockedAt: Instant? = null
)
