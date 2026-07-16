package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import ru.kavader.arepos.repository.DownloadAssetRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import kotlin.test.assertEquals

class DownloadsServiceReadableFileNameTest {

    private val service = DownloadsService(
        mock(DownloadAssetRepository::class.java),
        mock(FileStorageService::class.java),
        mock(UsersRepository::class.java),
        mock(ResourceAccessService::class.java),
        ObjectMapper()
    )

    @Test
    fun `keeps healthy stored filenames`() {
        assertEquals(
            "c4-composition-export.json",
            service.readableFileName("c4-composition-export.json", "C4 Composition")
        )
    }

    @Test
    fun `rebuilds mangled underscore filenames from title`() {
        assertEquals(
            "c4-composition.json",
            service.readableFileName("_4-composition-export.json", "C4 Composition")
        )
    }
}
