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
import no.uio.bedreflyt.api.model.simulation.RoomDistribution
import no.uio.bedreflyt.api.model.triplestore.Task
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.service.triplestore.*
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
    val mode: String,
    val smtMode: String = "changes"
)

data class RoomInfo(
    val patients: List<Patient>,
    val gender: String
)

typealias Room = String

typealias Allocation = Map<Room, RoomInfo?>

data class SimulationResponse(
    val allocations: List<List<Allocation>>,
    val changes: Int
)

data class ScenarioRequest(
    val batch: Int,
    val patientId: String?,
    val diagnosis: String?
)

data class SolverRequest(
    val no_rooms: Int,
    val capacities: List<Int>,
    val room_distances: List<Long>,
    val no_patients: Int,
    val genders: List<Boolean>,
    val infectious: List<Boolean>,
    val patient_distances: List<Int>,
    val previous: List<Int>,
    // options are c[hanges] to minimize number of room changes or
    // m[ax] to minimize maximum number of patients per room
    val mode: String
)

data class GlobalSolverRequest(
    val capacities: List<Int>,
    val room_distances: List<Long>,
    val genders: Map<String, Boolean>, // mapping from patientIds to gender (isMale)
    val infectious: Map<String, Boolean>, // patientId -> infectious
    val patient_distances: List<Map<String, Int>>, // patient_distances[i][j] = c means patient j is in category c on day i
    // options are c[hanges] to minimize number of room changes or
    // m[ax] to minimize maximum number of patients per room
    val mode: String
)

data class SolverResponse(
    val allocations: List<Allocation>,
    val changes: Int
)

// request for monte carlo sim
// the naming is a bit awkward since "SimulationRequest/Response" already exists
data class MultiSimulationRequest(
    val scenario: List<ScenarioRequest>,
    val repetitions: Int = 10,
    val risk: Double = 0.5,
    val smtMode: String = "changes"
)

data class MultiSimulationResponse(
    val runs: Int,
    val results: List<SimulationResponse>
)

@RestController
@RequestMapping("/api/simulation")
class SimulationController(
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

    private val log: Logger = Logger.getLogger(HomeController::class.java.name)

    /**
     * Execute the JAR
     *
     * Execute the JAR file to get the ABS model output
     *
     * @return String - Output of the JAR file
     */
    private fun executeJar(tempDir: Path): String {
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
                treatments.weightedChoice { it.frequency }.treatmentId
            }

            else -> {
                throw IllegalArgumentException("Unrecognized mode: should be one of \"worst\", \"common\", \"random\" or \"sample\"")
            }
        }
    }

    private fun createAndPopulatePatientTables(
        scenarioDbUrl: String,
        scenario: List<ScenarioRequest>,
        mode: String
    ): Map<String, Patient> {
        databaseService.createPatientTable(scenarioDbUrl)
        val patientsList = mutableMapOf<String, Patient>()

        scenario.forEach { scenarioRequest ->
            scenarioRequest.patientId?.let { patientId ->
                try {
                    val patients = patientService.findByPatientId(patientId)

                    if (patients.isNotEmpty()) {
                        patientsList[patientId] = patients[0]
                        databaseService.insertPatient(scenarioDbUrl, patients[0].patientId, patients[0].gender)
                        databaseService.insertPatientStatus(
                            scenarioDbUrl,
                            patients[0].patientId,
                            patients[0].infectious,
                            patients[0].roomNumber
                        )
                    } else {
                        throw IllegalArgumentException("Patient not found")
                    }
                } catch (e: EmptyResultDataAccessException) {
                    throw IllegalArgumentException("Patient not found")
                }


                scenarioRequest.diagnosis?.let { diagnosis ->
                    try {
                        val treatment = diagnosis + "_" + selectTreatmentByDiagnosisAndMode(diagnosis, mode)
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

        val treatments = treatmentService.getAllTreatments() ?: throw IllegalArgumentException("No treatments found")
        treatments.forEach { treatment ->
            val taskDependencies = taskDependencyService.getTaskDependenciesByTreatment(treatment.treatmentId)
                ?: throw IllegalArgumentException("No task dependencies found")

            // Insert the arrivals
            val arrival: Task = taskService.getTaskByTaskName("arrival")!!
            val appendName = treatment.diagnosis + "_" + treatment.treatmentId
            databaseService.insertTask(
                treatmentDbUrl,
                arrival.taskName + "_" + appendName,
                arrival.bed,
                arrival.averageDuration.toInt()
            )

            taskDependencies.forEach { taskDependency ->
                val treatmentName = taskDependency.diagnosis + "_" + treatment.treatmentId
                val task = taskService.getTaskByTaskName(taskDependency.task)
                    ?: throw IllegalArgumentException("No task found")
                databaseService.insertTask(
                    treatmentDbUrl,
                    task.taskName + "_" + treatmentName,
                    task.bed,
                    task.averageDuration.toInt()
                )
                databaseService.insertTaskDependency(
                    treatmentDbUrl,
                    treatmentName,
                    taskDependency.task + "_" + treatmentName,
                    taskDependency.dependsOn + "_" + treatmentName
                )
            }
        }

        log.info("Tables populated")
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
            databaseService.insertRoomDistribution(
                roomDbUrl, roomDistribution.roomNumber.toLong(), roomDistribution.roomNumberModel.toLong(),
                roomDistribution.room.toLong(), roomDistribution.capacity, roomDistribution.bathroom
            )
            simulationRoomDistribution.add(
                RoomDistribution(
                    roomDistribution.roomNumber, roomDistribution.roomNumberModel, roomDistribution.room.toString(),
                    roomDistribution.capacity, roomDistribution.bathroom
                )
            )
        }

        return simulationRoomDistribution
    }

    /**
     * Invoke the solver
     *
     * Invoke the solver with the patient data. For each patient, get the patient information and invoke the solver
     *
     * @param patients - Patient data
     * @return List<Allocation> - Solver response
     */
    private fun invokeSolver(
        patientList: List<String>,
        patientsSimulated: Map<String, Patient>,
        roomDistributions: List<RoomDistribution>,
        smtMode: String
    ): SolverResponse {
        val rooms = roomDistributions.size
        val capacities = roomDistributions.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = roomDistributions.map { it.room.toLong() ?: 0 }
        var patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val infectious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()
        val previous = mutableListOf<Int>()
        val patientMap = mutableMapOf<Int, Patient>()

        patientList.forEach { line ->
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
                        previous.add(if (patientsSimulated.containsKey(patientId)) patientInfo.roomNumber else -1)
                        patientMap[patientNumbers - 1] = patientInfo
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
            previous,
            smtMode
        )

        log.info("Invoking solver with  ${solverRequest.no_rooms} rooms, ${solverRequest.no_patients} patients in mode ${solverRequest.mode}")

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
                return SolverResponse(listOf(mapOf("error" to null)), -1)
            }

            // Parse the JSON string into a map
            // TODO: this does not work
            // response needs to be parsed into a dict of {"changes":Int, "allocations": alloc}
            // where alloc is the old "response.body"
            val mapper = jacksonObjectMapper()
            val jsonData: List<Map<String, Any>> = mapper.readValue(response.body!!)

            // Transform the data into the desired structure
            val transformedData: List<Allocation> = jsonData.flatMap { roomData ->
                roomData.map { (roomNumber, roomInfo) ->
                    val roomInfoMap = roomInfo as Map<*, *>
                    val patientNumbersMap = (roomInfoMap["patients"] as List<*>).map { it.toString().toInt() }
                    val patients = patientNumbersMap.map { number ->
                        val singlePatientMap = patientMap[number]!!
                        Patient(
                            patientId = singlePatientMap.patientId,
                            age = singlePatientMap.age
                        )
                    }
                    val gender = if (roomInfoMap["gender"] as String == "True") "Male" else "Female"

                    mapOf(
                        "Room ${roomNumber.toInt()}" to RoomInfo(patients, gender)
                    )
                }
            }

            return SolverResponse(transformedData, -1)
        } else {
            return SolverResponse(listOf(mapOf("warning" to null)), -1)
        }
    }

    /**
     * Simulate the scenario
     *
     * Execute the ABS model, get the various resources computed, take the single days and simulate the scenario
     *
     * @return List<String> - List of scenarios
     */
    private fun simulate(
        patients: Map<String, Patient>,
        roomDistributions: List<RoomDistribution>,
        tempDir: Path,
        smtMode: String
    ): SimulationResponse {
        try {
            val data = executeJar(tempDir)

            // If I got error from the JAR, return the error
            if (data.contains("Error executing JAR")) {
                throw RuntimeException(data)
            }

            // We need an Element Breaker to separate the information
            val information = data.split("------").filter { it.isNotEmpty() } // EB - split data over ------
            val groupedInformation: List<List<String>> =
                information.map { it -> it.split("\n").filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
            val scenarios = mutableListOf<List<Map<Room, RoomInfo>>>()

            var total_changes = 0
            groupedInformation.forEach { group ->
                val response = invokeSolver(group, patients, roomDistributions, smtMode)
                total_changes += response.changes
                val solveData = response.allocations
                if (solveData.isNotEmpty() && !solveData[0].containsKey("error") && !solveData[0].containsKey("warning")) {
                    solveData.forEach { roomData ->
                        roomData.forEach { (roomNumber, roomInfo) ->
                            if (roomInfo == null) {
                                log.warning("No room info for $roomNumber in $roomData")
                                throw Exception("No room info")
                            }
                            val allPatients = roomInfo.patients
                            val patientRoom = roomNumber.split(" ")[1].toInt()
                            allPatients.forEach { patient ->
                                patients[patient.patientId]?.let { patientInfo ->
                                    patientInfo.roomNumber = patientRoom
                                }
                            }
                        }

                    }
                }
                scenarios.add(solveData as List<Map<Room, RoomInfo>>)
                log.info(solveData.toString())
            }
            return SimulationResponse(scenarios, total_changes)
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return SimulationResponse(listOf(listOf(mapOf("error" to null))), -1)
        }
    }


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

        val roomDistributions = createAndPopulateRoomDistributions(bedreflytDB)
        val patients = createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

        val sim = simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode)
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }

    private fun simulateSmolScenarios(
        @SwaggerRequestBody(description = "Request to execute a simulation for room allocation") @RequestBody simulationRequest: MultiSimulationRequest,
    ): List<SimulationResponse> {
        log.info("Simulating ${simulationRequest.repetitions} scenarios with ${simulationRequest.scenario.size} requests")

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("simulation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)
        val roomDistributions = createAndPopulateRoomDistributions(bedreflytDB)
        createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        val runs = mutableListOf<SimulationResponse>()
        for (i in 1..simulationRequest.repetitions) {
            val mode = if (Random.nextDouble() <= simulationRequest.risk) "sample" else "worst"
            val patients = createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, mode)
            log.info("Patient table populated, invoking ABS with ${simulationRequest.scenario.size} requests")
            runs.add(simulate(patients, roomDistributions, tempDir, simulationRequest.smtMode))
            databaseService.clearTable(bedreflytDB, "scenario")
        }

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return runs.toList()
    }

    @PostMapping("/simulate-many")
    fun simulateAll(@SwaggerRequestBody(description = "Request to execute n simulations") @RequestBody simulationRequest: MultiSimulationRequest): ResponseEntity<List<SimulationResponse>> {
        val results = simulateSmolScenarios(simulationRequest)
        // if we want to do any preprocessing of the results, that goes here
        return ResponseEntity.ok(results)
    }

    private fun invokeGlobal(
        patientsSimulated: List<Map<Patient, Int>>,
        roomDistributions: List<RoomDistribution>,
        smtMode: String
    ): String? {
//            List<SolverResponse> {
        val capacities = roomDistributions.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = roomDistributions.map { it.room.toLong() ?: 0 }
        val genders = mutableMapOf<String, Boolean>()
        val infectious = mutableMapOf<String, Boolean>()
        val patient_categories: List<Map<String, Int>> = patientsSimulated.map { day ->
            day.mapKeys { it.key.patientId }
        }

        for (day in patientsSimulated) {
            for (patient in day.keys) {
                genders[patient.patientId] = patient.gender == "Male"
                infectious[patient.patientId] = patient.infectious
            }
        }

        val req: GlobalSolverRequest = GlobalSolverRequest(
            capacities,
            roomCategories,
            genders,
            infectious,
            patient_categories,
            smtMode
        )

        log.info("Invoking global solver with  ${roomDistributions.size} rooms, ${genders.size} patients in mode $smtMode")

        val restTemplate = RestTemplate()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val request = HttpEntity(req, headers)

        val solverEndpoint = environmentConfig.getOrDefault("SOLVER_ENDPOINT", "localhost")
        val solverUrl = "http://$solverEndpoint:8000/api/solve-global"
        log.info("Invoking global solver")
        val response = restTemplate.postForEntity(solverUrl, request, String::class.java)

        if (response.body!!.contains("Model is unsat")) {
            return "error"
            //return listOf(listOf(mapOf("error" to null)))
        }

        return response.body
    }

    private fun globalSolution(
        patients: Map<String, Patient>,
        roomDistributions: List<RoomDistribution>,
        tempDir: Path,
        smtMode: String
    ): String {
        //List<SimulationResponse> {
        try {
            val data = executeJar(tempDir)

            // If I got error from the JAR, return the error
            if (data.contains("Error executing JAR")) {
                throw RuntimeException(data)
            }

            // The ABS model retuns a string consisting of:
            // - a list of days, seperated by "------" where each day is
            // - a list of \n-separated patients, each consisting of [patientId, category]
            // we construct, for each day, a mapping of patientIds to categories
            val days = mutableListOf<Map<Patient, Int>>()
            for (day in data.split("------").filter { it.isNotEmpty() }) {
                val dayMap = mutableMapOf<Patient, Int>()
                for (patient in day.split("\n").filter {it.isNotEmpty()}) {
                    patient.split(",").let {
                        patients[it[0]]?.let { p ->
                            if (it[1].toInt() > 0) {
                                dayMap.put(p, it[1].toInt())
                            }
                        }
                    }
                }
                log.info("Å: Read day with requirements: $dayMap")
                if (dayMap.isNotEmpty()) { days.add(dayMap) }
            }
            val solverResponse = invokeGlobal(days, roomDistributions, "changes")
            return solverResponse?: "error"
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return "error"
            //return listOf(listOf(listOf(mapOf("error" to null))))
        }
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

        val roomDistributions = createAndPopulateRoomDistributions(bedreflytDB)
        val patients = createAndPopulatePatientTables(bedreflytDB, simulationRequest.scenario, simulationRequest.mode)
        createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${simulationRequest.scenario.size} requests")

        val sim = globalSolution(patients, roomDistributions, tempDir, simulationRequest.smtMode)
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        return ResponseEntity.ok(sim)
    }
}