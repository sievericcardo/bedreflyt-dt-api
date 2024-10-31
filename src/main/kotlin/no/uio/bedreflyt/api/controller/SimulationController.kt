package no.uio.bedreflyt.api.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.model.Scenario
import no.uio.bedreflyt.api.service.*
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
    private val patientService: PatientService,
    private val patientStatusService: PatientStatusService,
    private val scenarioService: ScenarioService,
    private val roomService: RoomService,
    private val roomDistributionService: RoomDistributionService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

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

        val rooms = roomService.findAll()
        rooms.forEach { room ->
            if (room != null) {
                roomService.saveRoom(room, roomDbUrl)
            }
        }

        val roomDistributions = roomDistributionService.findAll()
        roomDistributions.forEach { roomDistribution ->
            if (roomDistribution != null) {
                roomDistributionService.saveRoomDistribution(roomDistribution, roomDbUrl)
            }
        }

        val scenarioDbUrl = "jdbc:sqlite:scData.db"

        val patients = patientService.findAll()
        patients.forEach { patient ->
            if (patient != null) {
                patientService.savePatient(patient, scenarioDbUrl)
            }
        }

        val patientStatuses = patientStatusService.findAll()
        patientStatuses.forEach { patientStatus ->
            if (patientStatus != null) {
                patientStatusService.savePatientStatus(patientStatus, scenarioDbUrl)
            }
        }

        scenario.forEach { scenarioRequest ->
            val patient = patientService.findByPatientId(scenarioRequest.patientId!!, scenarioDbUrl)
            val newScenario = Scenario(
                batch = scenarioRequest.batch,
                patientId = patient,
                treatmentName = scenarioRequest.treatmentName!!
            )
            scenarioService.saveScenario(newScenario, scenarioDbUrl)
        }

        return ResponseEntity.ok("Scenario simulated")
    }
}