package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.service.DatabaseService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.service.live.RoomDistributionService
import no.uio.bedreflyt.api.service.live.RoomService
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

    /**
     * Execute the JAR
     *
     * Execute the JAR file to get the ABS model output
     *
     * @return String - Output of the JAR file
     */
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

    /**
     * Invoke the solver
     *
     * Invoke the solver with the patient data. For each patient, get the patient information and invoke the solver
     *
     * @param patient - Patient data
     * @return String - Solver response
     */
    private fun invokeSolver(patient : String) : String {
        val roomDistributions = roomDistributionService.findAll()
        val rooms = roomDistributions.size
        val capacities = roomDistributions.map { it?.capacity ?: 0 }
        val roomCategories = roomDistributions.map { it?.room?.id ?: 0 }
        var  patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val infectious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()

        val singlePatient = patient.split("\n")
        log.info("Single patient: $singlePatient")

        singlePatient.forEach { line ->
            val patientData = line.split(",")

            log.info("Patient data: $patientData")

            if (patientData.size > 1) {
                patientNumbers += 1
                val patientId = patientData[0]
                val patientDistance = patientData[1]

                log.info("Patient ID: $patientId")

                val patientInfoList = patientService.findByPatientId(patientId)
                if (patientInfoList.isNotEmpty()) {
                    val patientInfo = patientInfoList[0]
                    val gender = if (patientInfo.gender == "Male") true else false
                    genders.add(gender)
                    infectious.add(patientInfo.infectious)
                    patientDistances.add(patientDistance.toInt())
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
            patientDistances
        )

        log.info("Invoking solver with request: $solverRequest")

        if (patientNumbers > 0) {

            val restTemplate = RestTemplate()
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val request = HttpEntity(solverRequest, headers)

            val solverEndpoint = System.getenv().getOrDefault("SOLVER_ENDPOINT", "localhost")
            val solverUrl = "http://$solverEndpoint:8000/api/solve"
            val response = restTemplate.postForEntity(solverUrl, request, String::class.java)

            return (response.body!!)
        } else {
            return "No patients to solve"
        }
    }

    /**
     * Simulate the scenario
     *
     * Execute the ABS model, get the various resources computed, take the single days and simulate the scenario
     *
     * @return ResponseEntity<List<String>> - List of scenarios
     */
    private fun simulate() : ResponseEntity<List<String>> {
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

            val scenarios = mutableListOf<String>()

            groupedInformation.forEach { group ->
                group.forEach() { patient ->
                    val solveData = invokeSolver(patient)
                    scenarios.add(solveData)
                    log.info(solveData)
                }
            }

            return ResponseEntity.ok(scenarios)
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return ResponseEntity.internalServerError().body(listOf("Error executing JAR"))
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
    fun simulateScenario (@SwaggerRequestBody(description = "Request to sign in a new user") @RequestBody scenario: List<ScenarioRequest>): ResponseEntity<List<String>> {
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
                        return ResponseEntity.badRequest().body(listOf("Patient not found"))
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

        return simulate()
    }

    @Operation(summary = "Simulate a scenario using smol")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Scenario simulated"),
        ApiResponse(responseCode = "400", description = "Invalid scenario"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/smol-scenario")
    fun simulateSmolScenario (@SwaggerRequestBody(description = "Request to sign in a new user") @RequestBody scenario: List<ScenarioRequest>): ResponseEntity<List<String>> {
        log.info("Simulating scenario")
        val repl: REPL = replConfig.repl()

        val roomDbUrl = "roomData.db"
        databaseService.createRoomTables(roomDbUrl)

        val rooms =
            """
               SELECT * WHERE {
                ?obj a prog:Room ;
                    prog:Room_bedCategory ?bedCategory ;
                    prog:Room_roomDescription ?roomDescription .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(rooms)!!

        if (!resultRooms.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No rooms found"))
        }
        while (resultRooms.hasNext()) {
            val solution: QuerySolution = resultRooms.next()
            val roomId = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toLong()
            val roomDescription = solution.get("?roomDescription").asLiteral().toString()
            databaseService.insertRoom(roomDbUrl, roomId, roomDescription)
        }

        val roomDistributions =
            """
               SELECT * WHERE {
                ?obj a prog:RoomDistribution ;
                    prog:RoomDistribution_roomNumber ?roomNumber ;
                    prog:RoomDistribution_roomNumberModel ?roomNumberModel ;
                    prog:RoomDistribution_room ?room ;
                    prog:RoomDistribution_capacity ?capacity ;
                    prog:RoomDistribution_bathroom ?bathroom .
            }"""

        val resultRoomDistribution: ResultSet = repl.interpreter!!.query(roomDistributions)!!

        if (!resultRoomDistribution.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No room distributions found"))
        }
        while (resultRoomDistribution.hasNext()) {
            val solution: QuerySolution = resultRoomDistribution.next()
            val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toLong()
            val roomNumberModel = solution.get("?roomNumberModel").asLiteral().toString().split("^^")[0].toLong()
            val room = solution.get("?room").asLiteral().toString().split("^^")[0].toLong()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val bathroom = solution.get("?bathroom").asLiteral().toString().split("^^")[0].toBoolean()
            databaseService.insertRoomDistribution(roomDbUrl, roomNumber, roomNumberModel, room, capacity, bathroom)
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
                        return ResponseEntity.badRequest().body(listOf("Patient not found"))
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

        val treatmentDbUrl = "trData.db"
        databaseService.createTreatmentTables(treatmentDbUrl)

        val tasks =
            """
               SELECT * WHERE {
                ?obj a prog:Task ;
                    prog:Task_taskName ?taskName ;
                    prog:Task_durationAverage ?averageDuration ;
                    prog:Task_bed ?bedCategory .
            }"""

        val resultTasks: ResultSet = repl.interpreter!!.query(tasks)!!

        if (!resultTasks.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No tasks found"))
        }
        while (resultTasks.hasNext()) {
            val solution: QuerySolution = resultTasks.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            val bedCategory = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toInt()
            val averageDuration = solution.get("?averageDuration").asLiteral().toString().split("^^")[0].toDouble().toInt()
            databaseService.insertTask(treatmentDbUrl, taskName, bedCategory, averageDuration)
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
            return ResponseEntity.badRequest().body(listOf("No task dependencies found"))
        }
        while (resultTaskDependencies.hasNext()) {
            val solution: QuerySolution = resultTaskDependencies.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            val taskDependency = solution.get("?taskDependency").asLiteral().toString()
            databaseService.insertTaskDependency(treatmentDbUrl, taskName, taskDependency)
        }

        val treatments = """
            SELECT * WHERE {
                ?obj a prog:JourneyStep ;
                    prog:JourneyStep_diagnosis ?diagnosis ;
                    prog:JourneyStep_journeyOrder ?journeyOrder ;
                    prog:JourneyStep_task ?task .
            }"""

        val resultTreatments: ResultSet = repl.interpreter!!.query(treatments)!!

        if (!resultTreatments.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No treatments found"))
        }
        while (resultTreatments.hasNext()) {
            val solution: QuerySolution = resultTreatments.next()
            val diagnosis = solution.get("?diagnosis").asLiteral().toString()
            val orderInJourney = solution.get("?journeyOrder").asLiteral().toString().split("^^")[0].toInt()
            val task = solution.get("?task").asLiteral().toString()
            databaseService.insertTreatment(treatmentDbUrl, diagnosis, orderInJourney, task)
        }

        return simulate()
    }


}