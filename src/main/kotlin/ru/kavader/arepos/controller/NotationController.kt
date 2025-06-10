package ru.kavader.arepos.controller

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notation")
@CrossOrigin(origins = ["http://arepos.orb.local"])
class NotationController() {

    @PostMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun saveNotation(@PathVariable id: String, @RequestBody notation: String): ResponseEntity<String> {
        val logger = LoggerFactory.getLogger(NotationController::class.java)
        logger.info("Notation: $notation")
        return ResponseEntity.ok().body("OK")
    }

}