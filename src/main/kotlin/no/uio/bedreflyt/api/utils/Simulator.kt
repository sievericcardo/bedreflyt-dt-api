package no.uio.bedreflyt.api.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.parameters.RequestBody
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
import java.util.*
import java.util.logging.Logger
import java.util.logging.Level
import no.uio.bedreflyt.api.types.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import kotlin.random.Random

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
    fun executeJar(tempDir: Path): String {
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

    private fun invokeSolver(
        patientList: List<String>,
        patientsSimulated: Map<String, Patient>,
        roomDistributions: List<Room>,
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

            var total_changes = 0
            groupedInformation.forEach { group ->
                val response = invokeSolver(group, patients, rooms, smtMode)
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
                scenarios.add(solveData as List<Map<SingleRoom, RoomInfo>>)
                log.info(solveData.toString())
            }
            return SimulationResponse(scenarios, total_changes)
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
        val roomCategories: List<Long> = rooms.map { it.room.toLong() ?: 0 }
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
                log.info("Å: Read day with requirements: $dayMap")
                if (dayMap.isNotEmpty()) { days.add(dayMap) }
            }
            val solverResponse = invokeGlobal(days, rooms, "changes")
            return solverResponse?: "error"
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return "error"
            //return listOf(listOf(listOf(mapOf("error" to null))))
        }
    }
}