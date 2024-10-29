package no.uio.bedreflyt.api.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

@RestController
@RequestMapping("/api/simulation")
class SimulationController {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)
}