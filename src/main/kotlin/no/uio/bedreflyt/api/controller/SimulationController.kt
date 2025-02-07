package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.types.*
import no.uio.bedreflyt.api.utils.Simulator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.util.logging.Logger
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
        log.info("Å: Simulating ${simulationRequest.repetitions} scenarios with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)
        val roomDistributions = databaseService.createAndPopulateRooms(bedreflytDB)
        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        var worstCase : SimulationResponse? = null
        val runs = mutableListOf<SimulationResponse>()

        for (i in 1..simulationRequest.repetitions) {
            if (Random.nextDouble() > simulationRequest.risk) {
                if (worstCase == null) {
                    log.info("Å: Pre-computing worst-case scenario with ${simulationRequest.scenario.size} requests")
                    val patientsWC = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, "worst")
                    worstCase = simulator.simulate(patientsWC, roomDistributions, tempDir, simulationRequest.smtMode)
                    databaseService.clearTable(bedreflytDB, "scenario")
                    runs.add(worstCase)
                } else {
                    log.info("Å: Run $i / ${simulationRequest.repetitions}:\tReusing worst case")
                    runs.add(worstCase)
                }
                continue
            }
            val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, "sample")
            log.info("Å: Run $i / ${simulationRequest.repetitions}:\tPatient table populated, invoking ABS with ${simulationRequest.scenario.size} requests")
            runs.add(simulator.simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode))
            databaseService.clearTable(bedreflytDB, "scenario")
        }

//        val results = simulateSmolScenarios(simulationRequest)
        // if we want to do any preprocessing of the results, that goes here

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        log.info("Å: returning $runs")
        return ResponseEntity.ok(runs.toList())
    }
}