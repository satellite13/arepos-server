package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import ru.kavader.arepos.dto.user.UserProfileData
import ru.kavader.arepos.dto.user.UserProfilePatch
import kotlin.test.assertEquals

class UserProfileAttrsServiceTest {

    private val service = UserProfileAttrsService(ObjectMapper())

    @Test
    fun `reads empty profile when attrs contain invalid JSON`() {
        val profile = service.readProfile("{not json")

        assertEquals(UserProfileData(null, null, null, null), profile)
    }

    @Test
    fun `merges patch into empty attrs when existing attrs contain invalid JSON`() {
        val attrs = service.mergeProfile(
            "{not json",
            UserProfilePatch(
                firstName = "Ada",
                lastName = null,
                middleName = null,
                position = null
            )
        )

        assertEquals("""{"firstName":"Ada"}""", attrs)
    }
}
