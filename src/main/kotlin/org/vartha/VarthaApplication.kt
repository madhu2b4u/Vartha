package org.vartha

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SpringBootApplication
@EnableMongoRepositories
class VarthaApplication {

    @GetMapping
    fun sayHello(): String {
        return String.format("Welcome to the  news website")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(VarthaApplication::class.java, *args)
        }
    }
}