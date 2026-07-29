package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import ru.kavader.arepos.model.ShareResourceType
import kotlin.test.assertEquals

class CerbosAuthzModelTest {
    @Test
    fun `maps share resource types to cerbos resource kinds`() {
        assertEquals(CerbosResourceKind.MODEL, CerbosMappers.fromShareResourceType(ShareResourceType.MODEL))
        assertEquals(CerbosResourceKind.NOTATION, CerbosMappers.fromShareResourceType(ShareResourceType.NOTATION))
        assertEquals(CerbosResourceKind.NODE_TYPE, CerbosMappers.fromShareResourceType(ShareResourceType.NODE_TYPE))
        assertEquals(CerbosResourceKind.LINK_TYPE, CerbosMappers.fromShareResourceType(ShareResourceType.LINK_TYPE))
        assertEquals(CerbosResourceKind.NODE_SHAPE, CerbosMappers.fromShareResourceType(ShareResourceType.NODE_SHAPE))
        assertEquals(
            CerbosResourceKind.VALIDATION_SCRIPT,
            CerbosMappers.fromShareResourceType(ShareResourceType.VALIDATION_SCRIPT)
        )
    }
}
