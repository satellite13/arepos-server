package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

@Entity
@DynamicUpdate
@Table(
    name = "library_icons",
    schema = "public",
    indexes = [
        Index(name = "library_icons_name_uidx", columnList = "name", unique = true),
        Index(name = "library_icons_content_hash_idx", columnList = "content_hash")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class LibraryIcons(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "svg", nullable = false, columnDefinition = "text")
    var svg: String,

    @Column(name = "content_hash", nullable = false, length = 64)
    var contentHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: Users? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,
)
