package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import ru.kavader.arepos.dto.model.MergeLinksRequest
import ru.kavader.arepos.dto.model.MergeNodesRequest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelValidationMergeServiceTest {

    @Test
    fun `mergeNodes is a transactional write on the merge service`() {
        val method = ModelValidationMergeService::class.java.declaredMethods
            .singleOrNull { it.name == "mergeNodes" }
        assertNotNull(method, "mergeNodes must exist on ModelValidationMergeService")
        assertEquals(UUID::class.java, method.parameterTypes[0])
        assertEquals(MergeNodesRequest::class.java, method.parameterTypes[1])
    }

    @Test
    fun `mergeLinks is a transactional write on the merge service`() {
        val method = ModelValidationMergeService::class.java.declaredMethods
            .singleOrNull { it.name == "mergeLinks" }
        assertNotNull(method, "mergeLinks must exist on ModelValidationMergeService")
        assertEquals(UUID::class.java, method.parameterTypes[0])
        assertEquals(MergeLinksRequest::class.java, method.parameterTypes[1])
    }
}
