package ru.kavader.arepos.dto.model

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

enum class GraphDirection {
    IN,
    OUT,
    BOTH;

    companion object {
        fun parse(value: String): GraphDirection = when (value.lowercase()) {
            "in", "incoming" -> IN
            "out", "outgoing" -> OUT
            "both" -> BOTH
            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "direction must be one of: in, out, both"
            )
        }
    }
}

data class GraphNeighborResponse(
    val link: LinkResponse,
    val node: NodeResponse
)

data class DiagramReferenceResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val nodeId: UUID?
)
