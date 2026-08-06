package ru.kavader.arepos.service.diagramcopy

import org.junit.jupiter.api.Test
import ru.kavader.arepos.dto.model.DiagramCopyEntityKind
import ru.kavader.arepos.dto.model.DiagramCopyMatchReason
import ru.kavader.arepos.dto.model.DiagramCopyResolution
import ru.kavader.arepos.dto.model.DiagramCopyResolutionAction
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramCopyMatcherTest {

    private val matcher = DiagramCopyMatcher()

    @Test
    fun `node matches by stableId over name`() {
        val source = node("source", stableId = id("stable"))
        val targetWithSameName = node("target-name", name = source.name)
        val targetWithStableId = node("target-stable", stableId = source.stableId)

        val result = matcher.buildPreview(
            sourceNodes = listOf(source),
            sourceLinks = emptyList(),
            targetNodes = listOf(targetWithSameName, targetWithStableId),
            targetLinks = emptyList(),
            edges = emptyList(),
            resolutions = emptyList()
        )

        val preview = result.nodes.single()
        assertEquals(targetWithStableId.id, preview.autoMatchTargetId)
        assertEquals(DiagramCopyMatchReason.STABLE_ID, preview.autoMatchReason)
    }

    @Test
    fun `node matches by name and type when stableId missing`() {
        val source = node("source", stableId = id("source-stable"))
        val target = node("target", name = source.name, nodeTypeId = source.nodeTypeId)

        val result = matcher.buildPreview(
            sourceNodes = listOf(source),
            sourceLinks = emptyList(),
            targetNodes = listOf(target),
            targetLinks = emptyList(),
            edges = emptyList(),
            resolutions = emptyList()
        )

        val preview = result.nodes.single()
        assertEquals(target.id, preview.autoMatchTargetId)
        assertEquals(DiagramCopyMatchReason.NAME_AND_TYPE, preview.autoMatchReason)
    }

    @Test
    fun `ambiguous name and type leaves unresolved with candidates`() {
        val source = node("source")
        val firstCandidate = node("candidate-one", name = source.name, nodeTypeId = source.nodeTypeId)
        val secondCandidate = node("candidate-two", name = source.name, nodeTypeId = source.nodeTypeId)

        val result = matcher.buildPreview(
            sourceNodes = listOf(source),
            sourceLinks = emptyList(),
            targetNodes = listOf(firstCandidate, secondCandidate),
            targetLinks = emptyList(),
            edges = emptyList(),
            resolutions = emptyList()
        )

        val preview = result.nodes.single()
        assertEquals(null, preview.autoMatchTargetId)
        assertEquals(
            setOf(firstCandidate.id, secondCandidate.id),
            preview.candidates.map { it.id }.toSet()
        )
    }

    @Test
    fun `link matches by stableId`() {
        val source = link("source-link", stableId = id("link-stable"))
        val target = link("target-link", stableId = source.stableId)

        val result = matcher.buildPreview(
            sourceNodes = emptyList(),
            sourceLinks = listOf(source),
            targetNodes = emptyList(),
            targetLinks = listOf(target),
            edges = listOf(edge("edge", modelLinkId = source.id)),
            resolutions = emptyList()
        )

        val preview = result.links.single()
        assertEquals(target.id, preview.autoMatchTargetId)
        assertEquals(DiagramCopyMatchReason.STABLE_ID, preview.autoMatchReason)
    }

    @Test
    fun `link matches by type and matched endpoints`() {
        val sourceFrom = node("source-from", stableId = id("from-stable"), name = "From")
        val sourceTo = node("source-to", stableId = id("to-stable"), name = "To")
        val targetFrom = node("target-from", stableId = sourceFrom.stableId, name = "Target from")
        val targetTo = node("target-to", stableId = sourceTo.stableId, name = "Target to")
        val sourceLink = link(
            "source-link",
            stableId = id("source-link-stable"),
            sourceNodeId = sourceFrom.id,
            targetNodeId = sourceTo.id
        )
        val targetLink = link(
            "target-link",
            stableId = id("target-link-stable"),
            linkTypeId = sourceLink.linkTypeId,
            sourceNodeId = targetFrom.id,
            targetNodeId = targetTo.id
        )

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceFrom, sourceTo),
            sourceLinks = listOf(sourceLink),
            targetNodes = listOf(targetFrom, targetTo),
            targetLinks = listOf(targetLink),
            edges = listOf(edge("edge", modelLinkId = sourceLink.id)),
            resolutions = emptyList()
        )

        val preview = result.links.single()
        assertEquals(targetLink.id, preview.autoMatchTargetId)
        assertEquals(DiagramCopyMatchReason.ENDPOINTS_AND_TYPE, preview.autoMatchReason)
        assertEquals("From → To", preview.label)
    }

    @Test
    fun `edge blocker when endpoint skipped`() {
        val sourceNode = node("source")
        val edge = edge("edge", sourceNodeId = sourceNode.id)

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceNode),
            sourceLinks = emptyList(),
            targetNodes = emptyList(),
            targetLinks = emptyList(),
            edges = listOf(edge),
            resolutions = listOf(resolution(sourceNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.SKIP))
        )

        assertEquals(listOf(edge.edgeInstanceId), result.blockers.map { it.edgeInstanceId })
        assertTrue(result.nodes.single().isEndpointOfEdge)
    }

    @Test
    fun `canCommit false while blockers present`() {
        val sourceNode = node("source")

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceNode),
            sourceLinks = emptyList(),
            targetNodes = emptyList(),
            targetLinks = emptyList(),
            edges = listOf(edge("edge", sourceNodeId = sourceNode.id)),
            resolutions = listOf(resolution(sourceNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.SKIP))
        )

        assertFalse(result.canCommit)
    }

    @Test
    fun `link SKIP on referenced edge creates blocker and canCommit false`() {
        val sourceNode = node("source")
        val targetNode = node("target")
        val sourceLink = link(
            "source-link",
            sourceNodeId = sourceNode.id,
            targetNodeId = targetNode.id
        )

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceNode, targetNode),
            sourceLinks = listOf(sourceLink),
            targetNodes = emptyList(),
            targetLinks = emptyList(),
            edges = listOf(edge("edge", modelLinkId = sourceLink.id)),
            resolutions = listOf(
                resolution(sourceNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.CREATE),
                resolution(targetNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.CREATE),
                resolution(sourceLink.id, DiagramCopyEntityKind.LINK, DiagramCopyResolutionAction.SKIP)
            )
        )

        assertEquals(listOf("edge"), result.blockers.map { it.edgeInstanceId })
        assertFalse(result.canCommit)
    }

    @Test
    fun `unresolved endpoint produces blocker`() {
        val sourceNode = node("source")

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceNode),
            sourceLinks = emptyList(),
            targetNodes = emptyList(),
            targetLinks = emptyList(),
            edges = listOf(edge("edge", sourceNodeId = sourceNode.id)),
            resolutions = emptyList()
        )

        assertEquals(listOf("edge"), result.blockers.map { it.edgeInstanceId })
        assertFalse(result.canCommit)
    }

    @Test
    fun `canCommit true when edge link and endpoints are created`() {
        val sourceNode = node("source")
        val targetNode = node("target")
        val sourceLink = link(
            "source-link",
            sourceNodeId = sourceNode.id,
            targetNodeId = targetNode.id
        )

        val result = matcher.buildPreview(
            sourceNodes = listOf(sourceNode, targetNode),
            sourceLinks = listOf(sourceLink),
            targetNodes = emptyList(),
            targetLinks = emptyList(),
            edges = listOf(edge("edge", modelLinkId = sourceLink.id)),
            resolutions = listOf(
                resolution(sourceNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.CREATE),
                resolution(targetNode.id, DiagramCopyEntityKind.NODE, DiagramCopyResolutionAction.CREATE),
                resolution(sourceLink.id, DiagramCopyEntityKind.LINK, DiagramCopyResolutionAction.CREATE)
            )
        )

        assertTrue(result.blockers.isEmpty())
        assertTrue(result.canCommit)
    }

    private fun node(
        value: String,
        id: UUID = id("$value-id"),
        stableId: UUID = id("$value-stable"),
        name: String = "Name $value",
        nodeTypeId: UUID = id("node-type")
    ): MatchableNode = MatchableNode(id, stableId, name, nodeTypeId)

    private fun link(
        value: String,
        id: UUID = id("$value-id"),
        stableId: UUID = id("$value-stable"),
        linkTypeId: UUID = id("link-type"),
        sourceNodeId: UUID = id("$value-source"),
        targetNodeId: UUID = id("$value-target")
    ): MatchableLink = MatchableLink(id, stableId, linkTypeId, sourceNodeId, targetNodeId)

    private fun edge(
        edgeInstanceId: String,
        modelLinkId: UUID? = null,
        sourceNodeId: UUID? = null,
        targetNodeId: UUID? = null
    ): DiagramEdgeRef = DiagramEdgeRef(edgeInstanceId, modelLinkId, sourceNodeId, targetNodeId)

    private fun resolution(
        sourceId: UUID,
        kind: DiagramCopyEntityKind,
        action: DiagramCopyResolutionAction
    ): DiagramCopyResolution = DiagramCopyResolution(sourceId, action, kind = kind)

    private fun id(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray())
}
