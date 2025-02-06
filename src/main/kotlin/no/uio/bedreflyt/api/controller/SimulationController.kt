package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
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
    private val simulator: Simulator,
    private val patientAllocationService: PatientAllocationService,
    private val patientService: PatientService
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
    @PostMapping("/simulate")
    fun simulateSmolScenario(@SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody simulationRequest: SimulationRequest): ResponseEntity<SimulationResponse> {
        log.info("Simulating scenario with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)

        val rooms = databaseService.createAndPopulateRooms(bedreflytDB)
        val patients : Map<String, Patient> = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        val allocations : MutableMap<Patient, PatientAllocation> = mutableMapOf()
        patients.forEach { (_, patient) ->
            val allocation = PatientAllocation(patientId = patient, acute = false, diagnosisCode = "", diagnosisName = "", acuteCategory = 0, careCategory = 0, monitoringCategory = 0, careId = 0, contagious = false, roomNumber = -1)
            allocations[patient] = allocation
        }

        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

//        val sim = simulator.simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode)
        val simulationNeeds = simulator.computeDailyNeeds(tempDir) ?: throw Exception("Could not compute daily needs")
        val sim = simulator.simulate(simulationNeeds, patients, allocations, rooms, tempDir, simulationRequest.smtMode)

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }

    @PostMapping("/simulate-global")
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

        val needs = simulator.computeDailyNeeds(tempDir) ?: throw Exception("Could not compute daily needs")

        val sim = simulator.globalSolution(needs, patients, roomDistributions, tempDir, simulationRequest.smtMode)
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
        val rooms = databaseService.createAndPopulateRooms(bedreflytDB)
        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        val runs = mutableListOf<SimulationResponse>()
        // if the risk is 0 we only need to run one simulation – worst case is deterministic
        for (i in 1.. (if (simulationRequest.risk == 0.0) 1 else simulationRequest.repetitions)) {
            val mode = if (Random.nextDouble() <= simulationRequest.risk) "sample" else "worst"
            val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, mode)
            val allocations : MutableMap<Patient, PatientAllocation> = mutableMapOf()
            patients.forEach { (_, patient) ->
                val allocation = PatientAllocation(patientId = patient, acute = false, diagnosisCode = "", diagnosisName = "", acuteCategory = 0, careCategory = 0, monitoringCategory = 0, careId = 0, contagious = false, roomNumber = -1)
                allocations[patient] = allocation
            }

            log.info("Run $i / ${simulationRequest.repetitions}:\n\tPatient table populated, invoking ABS with ${simulationRequest.scenario.size} requests")
            val needs = simulator.computeDailyNeeds(tempDir) ?: throw Exception("Could not compute daily needs")
            runs.add(simulator.simulate(needs, patients, allocations, rooms, tempDir, simulationRequest.smtMode))
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