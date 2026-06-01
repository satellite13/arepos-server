package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.system.*
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/system")
class SystemController(
    private val buildProperties: BuildProperties
) {
    @GetMapping("/version")
    fun version(): VersionResponse = VersionResponse(
        version = buildProperties.version
    )
}

