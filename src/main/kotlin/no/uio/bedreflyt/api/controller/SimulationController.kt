package no.uio.bedreflyt.api.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.simulation.Scenario
import no.uio.bedreflyt.api.service.DatabaseService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.service.live.RoomDistributionService
import no.uio.bedreflyt.api.service.live.RoomService
import no.uio.bedreflyt.api.service.simulation.*
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.logging.Level
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.util.logging.Logger

data class ScenarioRequest(
    val batch : Int,
    val patientId : String?,
    val treatmentName : String?
)

data class SolverRequest (
    val no_rooms: Int,
    val capacities: List<Int>,
    val room_distances: List<Long>,
    val no_patients: Int,
    val genders: List<Boolean>,
    val infectious: List<Boolean>,
    val patient_distances: List<Int>,
)

@RestController
@RequestMapping("/api/simulation")
class SimulationController (
    private val replConfig: REPLConfig,
    private val databaseService: DatabaseService,
    private val patientService: PatientService,
    private val roomService : RoomService,
    private val roomDistributionService : RoomDistributionService,
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

    private fun executeJar() : String {
        val command = listOf("java", "-jar", "bedreflyt.jar")

        // Start the process
        val process = ProcessBuilder(command)
            .redirectErrorStream(false) // Do not redirect error stream so we can capture separately
            .start()

        // Capture output
        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
        }

        // Capture error output
        val errorOutput = StringBuilder()
        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                errorOutput.appendLine(line)
            }
        }

        // Wait for process to complete
        val exitCode = process.waitFor()

        // Return combined output or error output based on the exit code
        return if (exitCode == 0) {
            output.toString()
        } else {
            "Error executing JAR. Exit code: $exitCode\nError Output:\n$errorOutput"
        }
    }

    private fun invokeSolver(patient : String) : String {
        val roomDistributions = roomDistributionService.findAll()
        val rooms = roomDistributions.size
        val roomNumbers = roomDistributions.map { it?.roomNumberModel ?: 0 }
        val capacities = roomDistributions.map { it?.capacity ?: 0 }
        val roomCategories = roomDistributions.map { it?.room?.id ?: 0 }
        var  patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val infectious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()

        val singlePatient = patient.split("\n")
        singlePatient.forEach { line ->
            val patientData = line.split(",")

            if (patientData.size > 1) {
                patientNumbers += 1
                val patientId = patientData[0]
                val patientDistance = patientData[1]

                val patientInfo = patientService.findByPatientId(patientId)[0]
                if (patientInfo != null) {
                    val gender = if (patientInfo.gender == "Male") true else false
                    genders.add(gender)
                    infectious.add(patientInfo.infectious)
                    patientDistances.add(patientDistance.toInt())
                }
            }
        }

        // call the localhost:8000/api/solve passing all the lists as requirements
        val solverRequest = SolverRequest(
            rooms,
            capacities,
            roomCategories,
            patientNumbers,
            genders,
            infectious,
            patientDistances
        )

        if (patientNumbers > 0) {

            val restTemplate = RestTemplate()
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val reqeust = HttpEntity(solverRequest, headers)

            val response = restTemplate.postForEntity("http://localhost:8000/api/solve", reqeust, String::class.java)

            return (response.body!!)
        } else {
            return "No patients to solve"
        }
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

        val roomDbUrl = "roomData.db"
        databaseService.createRoomTables(roomDbUrl)

        val rooms = roomService.findAll()
        rooms.forEach { room ->
            if (room != null) {
                databaseService.insertRoom(roomDbUrl, room.id, room.roomDescription)
            }
        }

        val roomDistributions = roomDistributionService.findAll()
        roomDistributions.forEach { roomDistribution ->
            if (roomDistribution != null) {
                roomDistribution.room?.id?.let {
                    databaseService.insertRoomDistribution(roomDbUrl, roomDistribution.roomNumber, roomDistribution.roomNumberModel,
                        it, roomDistribution.capacity, roomDistribution.bathroom)
                }
            }
        }

        val scenarioDbUrl = "scData.db"
        databaseService.createPatientTable(scenarioDbUrl)

        val patients = patientService.findAll()
        patients.forEach { patient ->
            if (patient != null) {
                databaseService.insertPatient(scenarioDbUrl, patient.patientId, patient.gender)
                databaseService.insertPatientStatus(scenarioDbUrl, patient.patientId, patient.infectious, 0)
            }
        }

        scenario.forEach { scenarioRequest ->
            scenarioRequest.patientId?.let { patientId ->
                scenarioRequest.treatmentName?.let { treatmentName ->
                    try {
                        patientService.findByPatientId(patientId)
                    } catch (e: EmptyResultDataAccessException) {
                        return ResponseEntity.badRequest().body("Patient not found")
                    }

                    databaseService.insertScenario(
                        scenarioDbUrl,
                        scenarioRequest.batch,
                        scenarioRequest.patientId,
                        treatmentName
                    )
                }
            }
        }

        try {
            val data = executeJar()

            // We need an Element Breaker to separate the information
            val information = data.split("------").filter { it.isNotEmpty() } // EB - split data over ------

            val groupedInformation = mutableListOf<List<String>>()
            var currentGroup = mutableListOf<String>()

            for (item in information) {
                if (item.isNotEmpty()) {
                    currentGroup.add(item)
                } else {
                    if (currentGroup.isNotEmpty()) {
                        groupedInformation.add(currentGroup)
                        currentGroup = mutableListOf()
                    }
                }
            }

            if (currentGroup.isNotEmpty()) {
                groupedInformation.add(currentGroup)
            }

            groupedInformation.forEach { group ->
                group.forEach() { patient ->
                    val solveData = invokeSolver(patient)
                    log.info(solveData)
                }
            }
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return ResponseEntity.internalServerError().body("Error executing JAR")
        }

        return ResponseEntity.ok("Scenario simulated")
    }
}