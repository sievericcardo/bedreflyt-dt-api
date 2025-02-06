package no.uio.bedreflyt.api.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.simulation.Room
import no.uio.bedreflyt.api.types.RoomInfo
import no.uio.bedreflyt.api.types.SolverRequest
import no.uio.bedreflyt.api.types.SolverResponse
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import java.util.logging.Level
import no.uio.bedreflyt.api.types.*
import org.springframework.stereotype.Service

@Service
class Simulator (
    private val environmentConfig: EnvironmentConfig
) {

    private val log: Logger = Logger.getLogger(Simulator::class.java.name)

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

    private fun processSolverOutput(patientMap: Map<Int, Patient>, allocations: List<Map<String, Any>>) : List<Allocation> {
        return allocations.flatMap { roomData ->
            roomData.map { (roomNumber, roomInfo) ->
                val roomInfoMap = roomInfo as Map<String, Any>
                val patientNumbersMap = (roomInfoMap["patients"] as List<String>).map { it.toInt() }
                val patients = patientNumbersMap.map { number ->
                    val singlePatientMap = patientMap[number]!!
                    Patient(
                        patientId = singlePatientMap.patientId,
                        age = singlePatientMap.age
                    )
                }
                val gender = if (roomInfoMap["gender"] as String == "True") "Male" else "Female"

                mapOf(
                    "${roomNumber.toInt()}" to RoomInfo(patients, gender)
                )
            }
        }
    }

    private fun invokeSolver(
        solverRequest: SolverRequest,
        patientMap: Map<Int, Patient>, // Map of patient based on the order that is passed to the solver
    ) : SolverResponse {
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

        val mapper = jacksonObjectMapper()
        val responseMap: Map<String, Any> = mapper.readValue(response.body!!)

        // Extract changes and allocations from the response map
        val changes = responseMap["changes"] as Int
        val allocations = responseMap["allocations"] as List<Map<String, Any>>

        // Transform the allocations into the desired structure
        val transformedData: List<Allocation> = processSolverOutput(patientMap, allocations)

        // Return the transformed data along with the changes
        return SolverResponse(transformedData, changes)
    }

    private fun solve(
        patientListForDay: List<String>,
        patientsSimulated: Map<String, Patient>, // All patients that are simulated
        rooms: List<Room>,
        smtMode: String
    ): SolverResponse {
        val numberOfRooms = rooms.size
        val capacities = rooms.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = rooms.map { it.roomCategory ?: 0 }
        var patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val infectious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()
        val previous = mutableListOf<Int>()
        val patientMap = mutableMapOf<Int, Patient>()

        patientListForDay.forEach { line ->
            val patientData = line.split(",")
            val patientId = patientData[0]
            val patientDistance = patientData[1]

            patientsSimulated[patientId]?.let { patientInfo ->
                if (patientDistance.toInt() > 0) {
                    val gender = patientInfo.gender == "Male"
                    genders.add(gender)
                    infectious.add(patientInfo.infectious)
                    patientDistances.add(patientDistance.toInt())
                    previous.add(if (patientsSimulated.containsKey(patientId)) patientInfo.roomNumber else -1)
                    patientMap[patientNumbers] = patientInfo
                    patientNumbers += 1
                }
            }
        }

        // call the localhost:8000/api/solve passing all the lists as requirements
        val solverRequest = SolverRequest(
            numberOfRooms,
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

        return if (patientNumbers > 0) {
            invokeSolver(solverRequest, patientMap)
        } else {
            SolverResponse(listOf(mapOf("warning" to null)), -1)
        }
    }

    private fun processPatientMap(patients: Map<String, Patient>, solveData: List<Allocation>) {
        solveData.forEach { roomData ->
            roomData.forEach { (roomNumber, roomInfo) ->
                if (roomInfo == null) {
                    log.warning("No room info for $roomNumber in $roomData")
                    throw Exception("No room info")
                }
                val allPatients = roomInfo.patients
                // We use strings to index the map. Convert it to int
                val patientRoom = roomNumber.toInt()
                // Insert patient data
                allPatients.forEach { patient ->
                    patients[patient.patientId]?.let { patientInfo ->
                        patientInfo.roomNumber = patientRoom
                    }
                }
            }
        }
    }

    fun simulate(
        patients: Map<String, Patient>,
        rooms: List<Room>,
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
            val scenarios = mutableListOf<List<Map<SingleRoom, RoomInfo>>>()

            var totalChanges = 0
            groupedInformation.forEach { group ->
                // Solve each day
                val response = solve(group, patients, rooms, smtMode)
                log.info("Solved day with ${response.changes} changes")
                if (response.changes != -1) {totalChanges += response.changes}
                val solveData = response.allocations
                // We ignore the day that had an unsat model
                if (solveData.isNotEmpty() && !solveData[0].containsKey("error") && !solveData[0].containsKey("warning")) {
                    processPatientMap(patients, solveData)
                }
                scenarios.add(solveData as List<Map<SingleRoom, RoomInfo>>)
                log.info(solveData.toString())
            }
            return SimulationResponse(scenarios, totalChanges)
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return SimulationResponse(listOf(listOf(mapOf("error" to null))), -1)
        }
    }

    private fun invokeGlobal(
        patientsSimulated: List<Map<Patient, Int>>,
        rooms: List<Room>,
        smtMode: String
    ): String? {
//            List<SolverResponse> {
        val capacities = rooms.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = rooms.map { it.roomCategory ?: 0 }
        val genders = mutableMapOf<String, Boolean>()
        val infectious = mutableMapOf<String, Boolean>()
        val patientCategories: List<Map<String, Int>> = patientsSimulated.map { day ->
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
            patientCategories,
            smtMode
        )

        log.info("Invoking global solver with  ${rooms.size} rooms, ${genders.size} patients in mode $smtMode")

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

    fun globalSolution(
        patients: Map<String, Patient>,
        rooms: List<Room>,
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
                if (dayMap.isNotEmpty()) { days.add(dayMap) }
            }
            val solverResponse = invokeGlobal(days, rooms, smtMode)
            return solverResponse?: "error"
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return "error"
            //return listOf(listOf(listOf(mapOf("error" to null))))
        }
    }
}