package ru.kavader.arepos.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.FilesRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MdFileLinkValidatorTest {

    private val filesRepository: FilesRepository = Mockito.mock(FilesRepository::class.java)
    private val validator = MdFileLinkValidator(filesRepository, jacksonObjectMapper())

    @Test
    fun `validate rejects malformed attrs json`() {
        val ex = assertFailsWith<ResponseStatusException> {
            validator.validate("""{"broken": """)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `extractFileUuids rejects malformed attrs json`() {
        val ex = assertFailsWith<ResponseStatusException> {
            validator.extractFileUuids("""{"broken": """)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `validate resolves mdfile refs from valid json`() {
        val fileId = UUID.randomUUID()
        Mockito.`when`(filesRepository.existsById(fileId)).thenReturn(true)

        validator.validate("""{"doc":"mdfile://$fileId"}""")

        Mockito.verify(filesRepository, Mockito.times(1)).existsById(fileId)
    }

    @Test
    fun `validate rejects missing mdfile reference`() {
        val fileId = UUID.randomUUID()
        Mockito.`when`(filesRepository.existsById(fileId)).thenReturn(false)

        val ex = assertFailsWith<ResponseStatusException> {
            validator.validate("""{"doc":"mdfile://$fileId"}""")
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason?.contains(fileId.toString()) == true)
    }
}
