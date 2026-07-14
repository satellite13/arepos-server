package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.site.CreateTutorialRequest
import ru.kavader.arepos.dto.site.TutorialVideoResponse
import ru.kavader.arepos.dto.site.UpdateTutorialRequest
import ru.kavader.arepos.service.TutorialService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tutorials")
class TutorialsController(
    private val tutorialService: TutorialService
) {
    @GetMapping
    fun listPublished(): List<TutorialVideoResponse> = tutorialService.listPublished()

    @GetMapping("/admin")
    fun listAll(): List<TutorialVideoResponse> = tutorialService.listAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateTutorialRequest): TutorialVideoResponse =
        tutorialService.create(request)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateTutorialRequest
    ): TutorialVideoResponse = tutorialService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        tutorialService.delete(id)
    }
}
