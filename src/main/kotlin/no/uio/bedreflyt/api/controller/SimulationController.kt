package no.uio.bedreflyt.api.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.model.simulation.RoomDistribution
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import no.uio.microobject.runtime.REPL
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
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
    val previous: List<Int>
)

@RestController
@RequestMapping("/api/simulation")
class SimulationController (
    private val replConfig: REPLConfig,
    private val databaseService: DatabaseService,
    private val triplestoreService: TriplestoreService,
    private val patientService: PatientService,
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    /**
     * Execute the JAR
     *
     * Execute the JAR file to get the ABS model output
     *
     * @return String - Output of the JAR file
     */
    private fun executeJar() : String {
        val command = listOf("java", "-jar", "bedreflyt.jar")

        log.info("Executing JAR with command: $command")

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

        log.info("Standard Output size: ${output.length}")

        // Capture error output
        val errorOutput = StringBuilder()
        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                errorOutput.appendLine(line)
            }
        }

        log.info("Error Output: $errorOutput")

        // Wait for process to complete
        val exitCode = process.waitFor()

        log.info("Exit code: $exitCode")

        // Return combined output or error output based on the exit code
        return if (exitCode == 0) {
            output.toString()
        } else {
            "Error executing JAR. Exit code: $exitCode\nError Output:\n$errorOutput"
        }
    }

    private fun createAndPopulatePatientTables(scenarioDbUrl: String, scenario: List<ScenarioRequest>) {
        databaseService.createPatientTable(scenarioDbUrl)

        scenario.forEach { scenarioRequest ->
            scenarioRequest.patientId?.let { patientId ->
                try {
                    val patients = patientService.findByPatientId(patientId)

                    databaseService.insertPatient(scenarioDbUrl, patients[0].patientId, patients[0].gender)
                    databaseService.insertPatientStatus(scenarioDbUrl, patients[0].patientId, patients[0].infectious, patients[0].roomNumber)
                } catch (e: EmptyResultDataAccessException) {
                    throw IllegalArgumentException("Patient not found")
                }

                scenarioRequest.treatmentName?.let { treatmentName ->

                    databaseService.insertScenario(
                        scenarioDbUrl,
                        scenarioRequest.batch,
                        scenarioRequest.patientId,
                        treatmentName
                    )
                }
            }
        }
    }

    private fun createAndPopulateTreatmentTables(treatmentDbUrl: String, repl: REPL) {
        databaseService.createTreatmentTables(treatmentDbUrl)

        val tasks = triplestoreService.getAllTasks() ?: throw IllegalArgumentException("No tasks found")

        tasks.forEach { task ->
            databaseService.insertTask(treatmentDbUrl, task.taskName, task.bed, task.averageDuration.toInt())
        }

        val taskDependencies =
            """
           SELECT * WHERE {
            ?obj a prog:TaskDependency ;
                prog:TaskDependency_taskName ?taskName ;
                prog:TaskDependency_taskDependency ?taskDependency .
        }"""

        val resultTaskDependencies: ResultSet = repl.interpreter!!.query(taskDependencies)!!

        if (!resultTaskDependencies.hasNext()) {
            throw IllegalArgumentException("No task dependencies found")
        }
        while (resultTaskDependencies.hasNext()) {
            val solution: QuerySolution = resultTaskDependencies.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            val taskDependency = solution.get("?taskDependency").asLiteral().toString()
            databaseService.insertTaskDependency(treatmentDbUrl, taskName, taskDependency)
        }

        val treatments = triplestoreService.getAllTreatments() ?: throw IllegalArgumentException("No treatments found")

        treatments.forEach { treatment ->
            databaseService.insertTreatment(
                treatmentDbUrl,
                treatment.diagnosis,
                treatment.orderInJourney,
                treatment.task
            )
        }
    }

    private fun createAndPopulateRoomDistributions(roomDbUrl: String, repl: REPL): List<RoomDistribution> {
        val roomDistributions = triplestoreService.getAllRoomDistributions()
            ?: throw IllegalArgumentException("No room distributions found")
        val simulationRoomDistribution = mutableListOf<RoomDistribution>()

        roomDistributions.forEach { roomDistribution ->
            databaseService.insertRoomDistribution(roomDbUrl, roomDistribution.roomNumber.toLong(), roomDistribution.roomNumberModel.toLong(),
                roomDistribution.room.toLong(), roomDistribution.capacity, roomDistribution.bathroom)
            simulationRoomDistribution.add(RoomDistribution(roomDistribution.roomNumber, roomDistribution.roomNumberModel, roomDistribution.room.toString(),
                roomDistribution.capacity, roomDistribution.bathroom))
        }

        return simulationRoomDistribution
    }

    /**
     * Invoke the solver
     *
     * Invoke the solver with the patient data. For each patient, get the patient information and invoke the solver
     *
     * @param patient - Patient data
     * @return String - Solver response
     */
    private fun invokeSolver(patient : String, roomDistributions: List<RoomDistribution>) : List<Map<String, Any>> {
        val rooms = roomDistributions.size
        val capacities = roomDistributions.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = roomDistributions.map { it.room.toLong() ?: 0 }
        var  patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val infectious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()
        val previous = mutableListOf<Int>()

        val singlePatient = patient.split("\n")
        val patientMap = mutableMapOf<Int, Patient>()

        singlePatient.forEach { line ->
            val patientData = line.split(",")

            if (patientData.size > 1) {
                patientNumbers += 1
                val patientId = patientData[0]
                val patientDistance = patientData[1]

                val patientInfoList = patientService.findByPatientId(patientId)
                if (patientInfoList.isNotEmpty()) {
                    val patientInfo = patientInfoList[0]
                    val gender = patientInfo.gender == "Male"
                    genders.add(gender)
                    infectious.add(patientInfo.infectious)
                    patientDistances.add(patientDistance.toInt())
                    previous.add(patientInfo.roomNumber)

                    patientMap[patientNumbers-1] = patientInfoList[0]
                } else {
                    patientNumbers -= 1
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
            patientDistances,
            previous
        )

        log.info("Invoking solver with  ${solverRequest.no_rooms} rooms, ${solverRequest.no_patients} patients")

        if (patientNumbers > 0) {

            val restTemplate = RestTemplate()
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val request = HttpEntity(solverRequest, headers)

            val solverEndpoint = System.getenv().getOrDefault("SOLVER_ENDPOINT", "localhost")
            val solverUrl = "http://$solverEndpoint:8000/api/solve"
            val response = restTemplate.postForEntity(solverUrl, request, String::class.java)

            if (response.body!!.contains("Model is unsat")) {
                return listOf(mapOf("error" to "Model is unsatisfiable for ${solverRequest.no_rooms} rooms and ${solverRequest.no_patients} patients"))
            }

            // Parse the JSON string into a map
            val mapper = jacksonObjectMapper()
            val jsonData: List<Map<String, Any>> = mapper.readValue(response.body!!)

            // Transform the data into the desired structure
            val transformedData = jsonData.flatMap { roomData ->
                roomData.map { (roomNumber, roomInfo) ->
                    val roomInfoMap = roomInfo as Map<String, Any>
                    val patientNumbers = (roomInfoMap["patients"] as List<*>).map { it.toString().toInt() }
                    val patients = patientNumbers.map { number ->
                        val patient = patientMap[number]!!
                        mapOf(
                            "name" to patient.patientId,
                            "age" to patient.age
                        )
                    }
                    val gender = if (roomInfoMap["gender"] as String == "True") "Male" else "Female"

                    mapOf(
                        "Room $roomNumber" to mapOf(
                            "patients" to patients,
                            "gender" to gender
                        )
                    )
                }
            }

            return transformedData
        } else {
            return listOf(mapOf("warning" to "No patients found"))
        }
    }

    /**
     * Simulate the scenario
     *
     * Execute the ABS model, get the various resources computed, take the single days and simulate the scenario
     *
     * @return ResponseEntity<List<String>> - List of scenarios
     */
    private fun simulate(roomDistributions: List<RoomDistribution>) : ResponseEntity<List<List<Map<String, Any>>>> {
        try {
            val data = executeJar()

            // If I got error from the JAR, return the error
            if (data.contains("Error executing JAR")) {
                return ResponseEntity.internalServerError().body(listOf(listOf(mapOf("error" to data))))
            }

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

            val scenarios = mutableListOf<List<Map<String, Any>>>()

            groupedInformation.forEach { group ->
                group.forEach { patient ->
                    val solveData = invokeSolver(patient, roomDistributions)
                    scenarios.add(solveData)

//                    scenarios.add(solveData)
                    log.info(solveData.toString())
                }
            }

            return ResponseEntity.ok(scenarios)
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return ResponseEntity.internalServerError().body(listOf(listOf(mapOf("error" to "Error executing JAR"))))
        }
    }

    @Operation(summary = "Simulate a scenario for room allocation using smol")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Scenario simulated"),
        ApiResponse(responseCode = "400", description = "Invalid scenario"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/room-allocation-smol")
    fun simulateSmolScenario (@SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody scenario: List<ScenarioRequest>): ResponseEntity<List<List<Map<String, Any>>>> {
        log.info("Simulating scenario with ${scenario.size} requests")
        val repl: REPL = replConfig.repl()

        val roomDbUrl = "roomData.db"
        databaseService.createRoomTables(roomDbUrl)

        val roomList = triplestoreService.getAllRooms()

        if (roomList == null) {
            return ResponseEntity.badRequest().body(listOf(listOf(mapOf("error" to "No rooms found"))))
        }

        roomList.forEach() { room ->
            databaseService.insertRoom(roomDbUrl, room.bedCategory, room.roomDescription)
        }

        val roomDistributions = createAndPopulateRoomDistributions(roomDbUrl, repl)

        val scenarioDbUrl = "scData.db"
        createAndPopulatePatientTables(scenarioDbUrl, scenario)

        val treatmentDbUrl = "trData.db"
        createAndPopulateTreatmentTables(treatmentDbUrl, repl)

        log.info("Tables populated, invoking ABS with ${scenario.size} requests")

        val sim = simulate(roomDistributions)

        databaseService.deleteDatabase(roomDbUrl)
        databaseService.deleteDatabase(scenarioDbUrl)
        databaseService.deleteDatabase(treatmentDbUrl)

        return sim
    }
}