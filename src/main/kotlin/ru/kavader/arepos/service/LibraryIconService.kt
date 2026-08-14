package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.libraryicon.LibraryIconBundle
import ru.kavader.arepos.dto.libraryicon.LibraryIconBundleImportResult
import ru.kavader.arepos.dto.libraryicon.LibraryIconBundleItem
import ru.kavader.arepos.dto.libraryicon.LibraryIconCreateRequest
import ru.kavader.arepos.dto.libraryicon.LibraryIconResponse
import ru.kavader.arepos.model.LibraryIcons
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LibraryIconsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import java.time.Instant
import java.util.UUID

@Service
class LibraryIconService(
    private val repository: LibraryIconsRepository,
    private val usersRepository: UsersRepository,
    private val sanitizer: SvgSanitizer
) {
    @Transactional(readOnly = true)
    fun list(): List<LibraryIconResponse> =
        repository.findAll().sortedBy { it.name }.map { it.toResponse() }

    @Transactional(readOnly = true)
    fun findByNames(names: Collection<String>): List<LibraryIcons> {
        if (names.isEmpty()) return emptyList()
        val normalized = names.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) return emptyList()
        return repository.findAll().filter { it.name in normalized }
    }

    @Transactional
    fun create(request: LibraryIconCreateRequest): LibraryIconResponse {
        val name = LibraryIconNames.normalize(request.name)
        if (repository.existsByNameIgnoreCase(name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Icon '$name' already exists")
        }
        val svg = sanitizer.sanitize(request.svg)
        val now = Instant.now()
        val saved = repository.save(
            LibraryIcons(
                name = name,
                svg = svg,
                contentHash = sanitizer.contentHash(svg),
                createdBy = currentUserOrNull(),
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toResponse()
    }

    @Transactional
    fun createMany(requests: List<LibraryIconCreateRequest>): List<LibraryIconResponse> =
        requests.map { create(it) }

    @Transactional
    fun delete(id: UUID) {
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Icon $id not found")
        }
        repository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun exportBundle(): LibraryIconBundle {
        val icons = repository.findAll().sortedBy { it.name }.map { icon ->
            LibraryIconBundleItem(name = icon.name, svg = icon.svg)
        }
        return LibraryIconBundle(
            format = LibraryIconBundle.FORMAT,
            version = LibraryIconBundle.VERSION,
            exportedAt = Instant.now().toString(),
            icons = icons
        )
    }

    @Transactional
    fun importBundle(bundle: LibraryIconBundle): LibraryIconBundleImportResult {
        if (bundle.format != LibraryIconBundle.FORMAT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected format ${LibraryIconBundle.FORMAT}")
        }
        var created = 0
        var overwritten = 0
        val actor = currentUserOrNull()
        val now = Instant.now()
        for (item in bundle.icons) {
            val name = LibraryIconNames.normalize(item.name)
            val svg = sanitizer.sanitize(item.svg)
            val hash = sanitizer.contentHash(svg)
            val existing = repository.findByNameIgnoreCase(name)
            if (existing == null) {
                repository.save(
                    LibraryIcons(
                        name = name,
                        svg = svg,
                        contentHash = hash,
                        createdBy = actor,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                created += 1
            } else {
                existing.svg = svg
                existing.contentHash = hash
                existing.updatedAt = now
                repository.save(existing)
                overwritten += 1
            }
        }
        return LibraryIconBundleImportResult(created = created, overwritten = overwritten)
    }

    private fun currentUserOrNull(): Users? {
        val id = CurrentUser.getId() ?: return null
        return usersRepository.findById(id).orElse(null)
    }

    private fun LibraryIcons.toResponse(): LibraryIconResponse =
        LibraryIconResponse(
            id = requireNotNull(id),
            name = name,
            svg = svg,
            contentHash = contentHash,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
