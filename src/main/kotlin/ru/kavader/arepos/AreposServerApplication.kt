package ru.kavader.arepos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AreposServerApplication

fun main(args: Array<String>) {
	runApplication<AreposServerApplication>(*args)
}
