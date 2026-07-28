package ru.kavader.arepos.service.modelpackage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MdFileLinkRewriterTest {
    private val rewriter = MdFileLinkRewriter()

    @Test
    fun extractFromMarkdown() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val text = "See [x](mdfile://$a) and again mdfile://$a"
        assertEquals(setOf(a), rewriter.extractFileUuids(text))
    }

    @Test
    fun rewriteMarkdown() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val b = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val out = rewriter.rewrite("mdfile://$a", mapOf(a to b))
        assertEquals("mdfile://$b", out)
    }

    @Test
    fun rewriteDocumentFileIdInAttrsJson() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val b = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val attrs = """{"documentFileId":"$a","name":"n"}"""
        val out = rewriter.rewriteAttrsJson(attrs, mapOf(a to b))
        assertTrue(out!!.contains(b.toString()))
        assertTrue(!out.contains(a.toString()))
    }

    @Test
    fun extractDocumentFileIdFromAttrsJson() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val attrs = """{"documentFileId":"$a","name":"n"}"""
        assertEquals(setOf(a), rewriter.extractFromAttrsJson(attrs))
    }

    @Test
    fun extractNestedDocumentFileIdFromAttrsJson() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val attrs = """{"meta":{"documentFileId":"$a"}}"""
        assertEquals(setOf(a), rewriter.extractFromAttrsJson(attrs))
    }

    @Test
    fun extractNestedMdfileFromAttrsJson() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val b = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val attrs = """{"nested":{"note":"See mdfile://$a"},"items":["mdfile://$b"]}"""
        assertEquals(setOf(a, b), rewriter.extractFromAttrsJson(attrs))
    }
}
