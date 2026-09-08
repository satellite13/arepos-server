package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.DiagramCommentReadState
import ru.kavader.arepos.model.DiagramCommentReadStateKey
import java.util.UUID

interface DiagramCommentReadStateRepository :
    JpaRepository<DiagramCommentReadState, DiagramCommentReadStateKey> {
    fun findByKeyUserIdAndKeyDiagramId(userId: UUID, diagramId: UUID): DiagramCommentReadState?
}
