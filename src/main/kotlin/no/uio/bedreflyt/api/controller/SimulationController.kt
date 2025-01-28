package no.uio.bedreflyt.api.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.model.simulation.Room
import no.uio.bedreflyt.api.model.triplestore.Task
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.service.triplestore.*
import no.uio.bedreflyt.api.types.*
import no.uio.bedreflyt.api.utils.Simulator
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.logging.Level
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.util.logging.Logger
import kotlin.NoSuchElementException
import kotlin.random.Random

@RestController
@RequestMapping("/api/simulation")
class SimulationController(
    private val databaseService: DatabaseService,
    private val simulator: Simulator
) {

    private val log: Logger = Logger.getLogger(SimulationController::class.java.name)

    @Operation(summary = "Simulate a scenario for room allocation using smol")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Scenario simulated"),
            ApiResponse(responseCode = "400", description = "Invalid scenario"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(
                responseCode = "403",
                description = "Accessing the resource you were trying to reach is forbidden"
            ),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @PostMapping("/room-allocation-smol")
    fun simulateSmolScenario(@SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody simulationRequest: SimulationRequest): ResponseEntity<SimulationResponse> {
        log.info("Simulating scenario with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)

        val roomDistributions = databaseService.createAndPopulateRooms(bedreflytDB)
        val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

        val sim = simulator.simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode)
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }

    @PostMapping("/room-allocation-global")
    fun allocateGlobal(
        @SwaggerRequestBody(description = "Simulate a scenario, but return global solution")
        @RequestBody simulationRequest: SimulationRequest
    ): ResponseEntity<String> {
        //ResponseEntity<List<SimulationResponse>> {

        log.info("Simulating scenario with ${simulationRequest.scenario.size} requests")
        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)

        val roomDistributions = databaseService.createAndPopulateRooms(bedreflytDB)
        val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

        val sim = simulator.globalSolution(patients, roomDistributions, tempDir, simulationRequest.smtMode)
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }

    @PostMapping("/simulate-many")
    fun simulateAll(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request to execute n simulations") @org.springframework.web.bind.annotation.RequestBody simulationRequest: MultiSimulationRequest): ResponseEntity<List<SimulationResponse>> {
        log.info("Simulating ${simulationRequest.repetitions} scenarios with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)
        val roomDistributions = databaseService.createAndPopulateRooms(bedreflytDB)
        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        val runs = mutableListOf<SimulationResponse>()
        for (i in 1..simulationRequest.repetitions) {
            val mode = if (Random.nextDouble() <= simulationRequest.risk) "sample" else "worst"
            val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, mode)
            log.info("Patient table populated, invoking ABS with ${simulationRequest.scenario.size} requests")
            runs.add(simulator.simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode))
            databaseService.clearTable(bedreflytDB, "scenario")
        }

//        val results = simulateSmolScenarios(simulationRequest)
        // if we want to do any preprocessing of the results, that goes here

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(runs.toList())
    }
}