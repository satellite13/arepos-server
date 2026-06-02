package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import ru.kavader.arepos.dto.system.*
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "System metadata endpoints")
class SystemController(
    private val buildProperties: BuildProperties
) {
    @GetMapping("/version")
    @Operation(summary = "Get backend version")
    fun version(): VersionResponse = VersionResponse(
        version = buildProperties.version
    )
}

