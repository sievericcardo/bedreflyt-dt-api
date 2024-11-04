package no.uio.bedreflyt.api.controller

import java.util.logging.Logger
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HomeController {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    @GetMapping("/status")
    fun status(): String {
        log.info("Application is running")
        return "Application is running"
    }
}