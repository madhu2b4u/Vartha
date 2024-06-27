package org.vartha

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VarthaApplication

fun main(args: Array<String>) {
    runApplication<VarthaApplication>(*args)
}
