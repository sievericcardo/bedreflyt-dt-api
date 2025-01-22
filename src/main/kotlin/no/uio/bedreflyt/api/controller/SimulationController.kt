package no.uio.bedreflyt.api.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.constraints.Null
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.model.simulation.RoomDistribution
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.service.triplestore.*
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

data class SimulationRequest(
    val scenario: List<ScenarioRequest>,
    val mode: String
)

data class ScenarioRequest(
    val batch : Int,
    val patientId : String?,
    val diagnosis : String?
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
    private val environmentConfig: EnvironmentConfig,
    private val databaseService: DatabaseService,
    private val triplestoreService: TriplestoreService,
    private val patientService: PatientService,
    private val roomService: RoomService,
    private val roomDistributionService: RoomDistributionService,
    private val taskService: TaskService,
    private val taskDependencyService: TaskDependencyService,
    private val treatmentService: TreatmentService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    /**
     * Execute the JAR
     *
     * Execute the JAR file to get the ABS model output
     *
     * @return String - Output of the JAR file
     */
    private fun executeJar(tempDir: Path) : String {
        val jarFileName = "bedreflyt.jar"
        val jarFilePath = tempDir.resolve(jarFileName)
        Files.copy(Paths.get(jarFileName), jarFilePath, StandardCopyOption.REPLACE_EXISTING)

        val command = listOf("java", "-jar", jarFilePath.toString())

        log.info("Executing JAR with command: $command")

        // Start the process
        val process = ProcessBuilder(command)
            .directory(tempDir.toFile())
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

    private fun <T> List<T>.weightedChoice(weight: (T) -> Double): T {
        val totalWeights = this.sumOf(weight)
        val threshold: Double = Random.nextDouble(totalWeights)
        var seen: Double = 0.toDouble()
        for (elem in this) {
            seen += weight(elem)
            if (seen >= threshold) {
                return elem
            }
        }
        return this.last()
    }

    private fun selectTreatmentByDiagnosisAndMode(diagnosis: String, mode: String): String {
        val treatments: List<Treatment> = treatmentService.getAllTreatmentsByDiagnosis(diagnosis)
        if (treatments.isEmpty()) {
            throw NoSuchElementException("No treatment found for diagnosis: $diagnosis")
        }
        return when (mode) {
            "worst" -> {
                treatments.maxByOrNull { it.weight }!!.treatmentId
            }
            "common" -> {
                treatments.maxByOrNull { it.frequency }!!.treatmentId
            }
            "random" -> {
                treatments.random().treatmentId
            }
            "sample" -> {
                treatments.weightedChoice {it.frequency} .treatmentId
            }
            else -> {
                throw IllegalArgumentException("Unrecognized mode: should be one of \"worst\", \"common\", \"random\" or \"sample\"")
            }
        }
    }

    private fun createAndPopulatePatientTables(scenarioDbUrl: String, scenario: List<ScenarioRequest>, mode: String) : Map<String, Patient> {
        databaseService.createPatientTable(scenarioDbUrl)
        val patientsList = mutableMapOf<String, Patient>()

        scenario.forEach { scenarioRequest ->
            scenarioRequest.patientId?.let { patientId ->
                try {
                    val patients = patientService.findByPatientId(patientId)

                    if (patients.isNotEmpty()) {
                        patientsList[patientId] = patients[0]
                        databaseService.insertPatient(scenarioDbUrl, patients[0].patientId, patients[0].gender)
                        databaseService.insertPatientStatus(scenarioDbUrl, patients[0].patientId, patients[0].infectious, patients[0].roomNumber)
                    } else {
                        throw IllegalArgumentException("Patient not found")
                    }
                } catch (e: EmptyResultDataAccessException) {
                    throw IllegalArgumentException("Patient not found")
                }


                scenarioRequest.diagnosis?.let { diagnosis ->
                    try {
                        val treatment = diagnosis + "_" + selectTreatmentByDiagnosisAndMode(diagnosis, mode)
                        log.info("Å: Patient $patientId assigned treatment $treatment")
                        databaseService.insertScenario(
                            scenarioDbUrl,
                            scenarioRequest.batch,
                            scenarioRequest.patientId,
                            treatment
                        )
                    } catch (e: IllegalArgumentException) {
                        throw e
                    }
                }
            }
        }
        return patientsList
    }

    private fun createAndPopulateTreatmentTables(treatmentDbUrl: String) {
        databaseService.createTreatmentTables(treatmentDbUrl)

        val tasks = taskService.getAllTasks() ?: throw IllegalArgumentException("No tasks found")

        tasks.forEach { task ->
            databaseService.insertTask(treatmentDbUrl, task.taskName, task.bed, task.averageDuration.toInt())
        }

        val treatments = treatmentService.getAllTreatments() ?: throw IllegalArgumentException("No treatments found")
                treatments.forEach() { treatment ->
                    val taskDependencies = taskDependencyService.getTaskDependenciesByTreatment(treatment.treatmentId)?: throw IllegalArgumentException("No task dependencies found")
                    taskDependencies.forEach { taskDependency ->
                        databaseService.insertTaskDependency(treatmentDbUrl,
                            taskDependency.diagnosis + "_" + treatment.treatmentId,
                            taskDependency.task,
                            taskDependency.dependsOn)
                    }
                }

        log.info("Treatment tables populated")
    }

    private fun createAndPopulateRoomDistributions(roomDbUrl: String): List<RoomDistribution> {
        val roomList = roomService.getAllRooms()

        roomList?.let {
            it.forEach { room ->
                databaseService.insertRoom(roomDbUrl, room.bedCategory, room.roomDescription)
            }
        } ?: throw IllegalArgumentException("No rooms found")

        val roomDistributions = roomDistributionService.getAllRoomDistributions()
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
    private fun invokeSolver(patient : String, patientsSimulated: Map<String, Patient>, roomDistributions: List<RoomDistribution>) : List<Map<String, Any>> {
        val rooms = roomDistributions.size
        val capacities = roomDistributions.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = roomDistributions.map { it.room.toLong() ?: 0 }
        var patientNumbers = 0
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

                val patientInfo = patientsSimulated[patientId]
                if (patientInfo == null) {
                    patientNumbers -= 1
                } else {
                    if (patientDistance.toInt() > 0) {
                        val gender = patientInfo.gender == "Male"
                        genders.add(gender)
                        infectious.add(patientInfo.infectious)
                        patientDistances.add(patientDistance.toInt())
                        previous.add(patientInfo.roomNumber)

                        patientMap[patientNumbers-1] = patientInfo
                    } else {
                        patientNumbers -= 1
                    }
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

            val solverEndpoint = environmentConfig.getOrDefault("SOLVER_ENDPOINT", "localhost")
            val solverUrl = "http://$solverEndpoint:8000/api/solve"
            log.info("Invoking solver with request: $request")
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
                    val roomInfoMap = roomInfo as Map<*, *>
                    val patientNumbersMap = (roomInfoMap["patients"] as List<*>).map { it.toString().toInt() }
                    val patients = patientNumbersMap.map { number ->
                        val singlePatientMap = patientMap[number]!!
                        mapOf(
                            "name" to singlePatientMap.patientId,
                            "age" to singlePatientMap.age
                        )
                    }
                    val gender = if (roomInfoMap["gender"] as String == "True") "Male" else "Female"

                    mapOf(
                        "Room ${roomDistributions[roomNumber.toInt()].roomNumber}" to mapOf(
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
     * @return List<String> - List of scenarios
     */
    private fun simulate(patients: Map<String, Patient>, roomDistributions: List<RoomDistribution>, tempDir: Path) : List<List<Map<String, Any>>> {
        try {
            val data = executeJar(tempDir)

            // If I got error from the JAR, return the error
            if (data.contains("Error executing JAR")) {
                return listOf(listOf(mapOf("error" to data)))
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
                    val solveData = invokeSolver(patient, patients, roomDistributions)
                    if (solveData.isNotEmpty() && !solveData[0].containsKey("error") && !solveData[0].containsKey("warning")) {
                        solveData.forEach { roomData ->
                            roomData.forEach { (roomNumber, roomInfo) ->
                                val roomInfoMap = roomInfo as Map<String, Any>
                                val allPatients = roomInfoMap["patients"] as List<Map<String, Any>>
                                allPatients.forEach { patient ->
                                    val patientId = patient["name"] as String
                                    val patientRoom = roomNumber.split(" ")[1].toInt()
                                    patients[patientId]?.let { patientInfo ->
                                        patientInfo.roomNumber = patientRoom
                                    }
                                }
                            }
                        }
                    }
                    scenarios.add(solveData)
                    log.info(solveData.toString())
                }
            }

            return scenarios
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return listOf(listOf(mapOf("error" to "Error executing JAR")))
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
    fun simulateSmolScenario (@SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody simulationRequest: SimulationRequest): ResponseEntity<List<List<Map<String, Any>>>> {
        log.info("Simulating scenario with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)

        val roomDistributions = createAndPopulateRoomDistributions(bedreflytDB)
        val patients = createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

        val sim = simulate(patients, roomDistributions, tempDir)
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }

    private fun simulateSmolScenarios (@SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody simulationRequest: SimulationRequest, numberOfRuns : Int): List<List<List<Map<String, Any>>>> {
        log.info("Simulating $numberOfRuns scenarios with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)
        val roomDistributions = createAndPopulateRoomDistributions(bedreflytDB)
        createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        val runs = mutableListOf<List<List<Map<String, Any>>>>()
        for (i in 1..numberOfRuns) {
            val patients = createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, "sample")
            log.info("Patient table populated, invoking ABS with ${simulationRequest.scenario.size} requests")
            runs.add(simulate(patients, roomDistributions, tempDir))
            databaseService.clearTable(bedreflytDB, "scenario")
        }

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return runs.toList()
    }
    /**
     * Given a list of scenarios, accumulate the information
     *
     * @return ResponseEntity<String> – whatever we decide an interesting response is. A string for now
     */
    private fun collectScenarios (simulations: List<List<List<Map<String, Any>>>>): ResponseEntity<List<String>> {
        var results = mutableListOf<String>()
        for ((i, sim) in simulations.withIndex()) {
            log.info("Starting simulation $i")
            var unsatDays = 0
            for (day in sim) {
                for (room in day) {
                    if (room.containsKey("error")) {
                        unsatDays += 1
                    }
                }
            }
            log.info("$unsatDays out of ${sim.size} where unsatisfiable in simulation ${i+1}")
            results.add("$unsatDays out of ${sim.size} where unsatisfiable in simulation ${i+1}")
        }
        return ResponseEntity.ok(results)
    }

    @PostMapping("/simulate-many")
    fun simulateAll(@SwaggerRequestBody(description = "Request to execute n simulations") @RequestBody simulationRequest: SimulationRequest): ResponseEntity<List<String>> {
        return collectScenarios(simulateSmolScenarios(simulationRequest, 10))
    }
}