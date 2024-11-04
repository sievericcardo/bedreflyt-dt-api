package no.uio.bedreflyt.api.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.simulation.ScenarioSim
import no.uio.bedreflyt.api.service.simulation.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.util.logging.Logger

data class ScenarioRequest(
    val batch : Int,
    val patientId : String?,
    val treatmentName : String?
)

@RestController
@RequestMapping("/api/simulation")
class SimulationController (
    private val replConfig: REPLConfig,
    private val patientSimService: PatientSimService,
    private val patientStatusSimService: PatientStatusSimService,
    private val scenarioSimService: ScenarioSimService,
    private val roomSimService: RoomSimService,
    private val roomDistributionSimService: RoomDistributionSimService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    @PostMapping("/smol")
    fun smol() : ResponseEntity<String> {
        val smolPath = System.getenv("SMOL_PATH") ?: "Bedreflyt.smol"

        // get the repl from the config
        val repl = replConfig.repl()
        repl.command("read", smolPath)
        repl.command("auto", "")

        return ResponseEntity.ok("SMOL file read")
    }

    @Operation(summary = "Simulate a scenario")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Scenario simulated"),
        ApiResponse(responseCode = "400", description = "Invalid scenario"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/scenario")
    fun simulateScenario (@SwaggerRequestBody(description = "Request to sign in a new user") @RequestBody scenario: List<ScenarioRequest>): ResponseEntity<String> {
        log.info("Simulating scenario")
        val roomDbUrl = "jdbc:sqlite:roomData.db"

        val rooms = roomSimService.findAll()
        rooms.forEach { room ->
            if (room != null) {
                roomSimService.saveRoom(room)
            }
        }

        val roomDistributions = roomDistributionSimService.findAll()
        roomDistributions.forEach { roomDistribution ->
            if (roomDistribution != null) {
                roomDistributionSimService.saveRoomDistribution(roomDistribution)
            }
        }

        val scenarioDbUrl = "jdbc:sqlite:scData.db"

        val patients = patientSimService.findAll()
        patients.forEach { patient ->
            if (patient != null) {
                patientSimService.savePatientSim(patient)
            }
        }

        val patientStatuses = patientStatusSimService.findAll()
        patientStatuses.forEach { patientStatus ->
            if (patientStatus != null) {
                patientStatusSimService.savePatientStatus(patientStatus)
            }
        }

        scenario.forEach { scenarioRequest ->
            val patient = patientSimService.findByPatientId(scenarioRequest.patientId!!)
            val newScenarioSim = ScenarioSim(
                batch = scenarioRequest.batch,
                patientId = patient,
                treatmentName = scenarioRequest.treatmentName!!
            )
            scenarioSimService.saveScenario(newScenarioSim, scenarioDbUrl)
        }

        return ResponseEntity.ok("Scenario simulated")
    }
}