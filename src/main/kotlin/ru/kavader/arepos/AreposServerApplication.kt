package ru.kavader.arepos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AreposServerApplication

fun main(args: Array<String>) {
	runApplication<AreposServerApplication>(*args)
}
