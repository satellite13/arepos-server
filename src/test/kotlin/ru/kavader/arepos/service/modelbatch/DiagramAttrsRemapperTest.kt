package ru.kavader.arepos.service.modelbatch

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class DiagramAttrsRemapperTest {

    private val remapper = DiagramAttrsRemapper(ObjectMapper())

    @Test
    fun `keeps invalid JSON attrs unchanged`() {
        val attrs = "{not json"

        val result = remapper.remap(
            attrs,
            mapOf("draft-node" to UUID.randomUUID()),
            mapOf("draft-link" to UUID.randomUUID())
        )

        assertEquals(attrs, result)
    }
}
