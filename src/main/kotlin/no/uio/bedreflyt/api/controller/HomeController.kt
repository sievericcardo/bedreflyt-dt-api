package no.uio.bedreflyt.api.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class HomeController {

    private val log : Logger = LoggerFactory.getLogger(HomeController::class.java.name)

    @GetMapping("/status")
    fun status(): String {
        log.info("Application is running")
        return "Application is running"
    }
}