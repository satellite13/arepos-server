package ru.kavader.arepos.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.metrics.CustomMetricsService

@RestController
class HelloWorldController(val metrics: CustomMetricsService) {
    @GetMapping("/hello-world", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun helloWorld(): String {
        metrics.incrementHelloWorldCounter()
        return "Hello World"
    }
}