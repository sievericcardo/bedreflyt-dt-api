package no.uio.bedreflyt.api.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.model.simulation.Room
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

@Service
class Simulator (
    private val environmentConfig: EnvironmentConfig,
    private val patientService: PatientService,
    private val patientAllocationService: PatientAllocationService
) {

    private val log: Logger = Logger.getLogger(Simulator::class.java.name)
    private val roomMap: MutableMap<Int, Int> = mutableMapOf()
    private val indexRoomMap : MutableMap<Int, Int> = mutableMapOf()

    private fun processDailyNeeds(needs: String) : SimulationNeeds {
        val information = needs.split("------").filter { it.isNotEmpty() } // EB - split data over ------
        val groupedInformation: List<List<String>> =
            information.map { it -> it.split("\n").filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }

        return groupedInformation.map { group ->
            group.map { line ->
                val patientData = line.split(",")
                val patientId = patientData[0]
                val patientDistance = patientData[1]
                Pair(patientService.findByPatientId(patientId)!!, patientDistance.toInt())
            }.toMutableList()
        }
    }

    /**
     * Execute the JAR
     *
     * Execute the JAR file to get the ABS model output
     *
     * @return String - Output of the JAR file -- we will use to get the whole trace of days
     */
    fun computeDailyNeeds(tempDir: Path): SimulationNeeds? {
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
            processDailyNeeds(output.toString())
        } else {
            null
        }
    }

    private fun processSolverOutput(patientMap: Map<Int, Patient>, allocations: List<Map<String, Any>>) : List<Allocation> {
        return allocations.flatMap { roomData ->
            roomData.map { (roomNumber, roomInfo) ->
                val roomInfoMap = roomInfo as Map<String, Any>
                val patientNumbersMap = (roomInfoMap["patients"] as List<String>).map { it.toInt() }
                val patients = patientNumbersMap.map { number ->
                    val singlePatientMap = patientMap[number]!!
                    patientService.findByPatientId(singlePatientMap.patientId)!!
                }
                val gender = if (roomInfoMap["gender"] as String == "True") "Male" else "Female"

                mapOf(
                    "${roomMap[roomNumber.toInt()]}" to RoomInfo(patients, gender)
                )
            }
        }
    }

    private fun invokeSolver(
        solverRequest: SolverRequest,
        patientMap: Map<Int, Patient>, // Map of patient based on the order that is passed to the solver
    ) : SolverResponse {
        val solverEndpoint = environmentConfig.getOrDefault("SOLVER_ENDPOINT", "localhost")
        val solverUrl = "http://$solverEndpoint:8000/api/solve"
        val connection = URI(solverUrl).toURL().openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val jsonBody = jacksonObjectMapper().writeValueAsString(solverRequest)

        log.info("Invoking solver with request")
        OutputStreamWriter(connection.outputStream).use {
            it.write(jsonBody)
        }

        if (connection.responseCode != 200) {
            log.warning("Solver returned status code ${connection.responseCode}")
            return SolverResponse(listOf(mapOf("error" to null)), -1)
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }

        if (response.contains("Model is unsat")) {
            return SolverResponse(listOf(mapOf("error" to null)), -1)
        }

        val mapper = jacksonObjectMapper()
        val responseMap: Map<String, Any> = mapper.readValue(response)

        // Extract changes and allocations from the response map
        val changes = responseMap["changes"] as Int
        val allocations = responseMap["allocations"] as List<Map<String, Any>>

        // Transform the allocations into the desired structure
        val transformedData: List<Allocation> = processSolverOutput(patientMap, allocations)

        // Return the transformed data along with the changes
        return SolverResponse(transformedData, changes)
    }

    private fun solve(
        dailyNeeds: DailyNeeds,
        patientsSimulated: Map<String, Patient>, // All patients that are simulated
        allocations: Map<Patient, PatientAllocation>,
        rooms: List<Room>,
        smtMode: String
    ): SolverResponse {
        val numberOfRooms = rooms.size
        val capacities = rooms.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = rooms.map { it.roomCategory ?: 0 }
        var patientNumbers = 0
        val genders = mutableListOf<Boolean>()
        val contagious = mutableListOf<Boolean>()
        val patientDistances = mutableListOf<Int>()
        val previous = mutableListOf<Int>()
        val patientMap = mutableMapOf<Int, Patient>()

        dailyNeeds.forEach { dailyNeed ->
            val patientId = dailyNeed.first.patientId
            val patientDistance = dailyNeed.second

            patientsSimulated[patientId]?.let { patientInfo ->
                if (patientDistance.toInt() > 0) {
                    val gender = patientInfo.gender == "Male"
                    genders.add(gender)
                    val singlePatient = patientService.findByPatientId(patientInfo.patientId)!!
                    contagious.add(allocations[singlePatient]!!.contagious)
                    patientDistances.add(patientDistance.toInt())
                    previous.add(allocations[singlePatient]!!.roomNumber)
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
            contagious,
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

    private fun processPatientMap(patients: Map<String, Patient>, allocations: Map<Patient, PatientAllocation>, solveData: List<Allocation>) {
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
                    patients[patient.patientId]?.let {
                        allocations[patient]!!.roomNumber = indexRoomMap[patientRoom]!!
                    }
                }
            }
        }
    }

    fun simulate(
        needs: SimulationNeeds,
        patients: Map<String, Patient>,
        allocations: Map<Patient, PatientAllocation>,
        rooms: List<Room>,
        tempDir: Path,
        smtMode: String
    ): SimulationResponse {
        val scenarios = mutableListOf<List<Map<SingleRoom, RoomInfo>>>()
        rooms.forEachIndexed { index, room -> roomMap[index] = room.roomNumber }
        roomMap.forEach { (key, value) -> indexRoomMap[value] = key }
        try {
            var totalChanges = 0
            needs.forEach { group ->
                // Solve each day
                val response = solve(group, patients, allocations, rooms, smtMode)
                log.info("Solved day with ${response.changes} changes")
                if (response.changes != -1) {totalChanges += response.changes}
                val solveData = response.allocations
                // We ignore the day that had an unsat model
                if (solveData.isNotEmpty() && !solveData[0].containsKey("error") && !solveData[0].containsKey("warning")) {
                    processPatientMap(patients, allocations, solveData)
                }
                scenarios.add(solveData as List<Map<SingleRoom, RoomInfo>>)
                log.info(solveData.toString())
            }

            return SimulationResponse(scenarios, totalChanges)
        } catch (e: Exception) {
            "Error executing Solver: ${e.message}"
            log.log(Level.SEVERE, "Error executing solver", e)
            return SimulationResponse(listOf(listOf(mapOf("error" to null))), -1)
        }
    }

    private fun invokeGlobal(
        patientsSimulated: SimulationNeeds,
        rooms: List<Room>,
        smtMode: String
    ): String? {
        val capacities = rooms.map { it.capacity ?: 0 }
        val roomCategories: List<Long> = rooms.map { it.roomCategory ?: 0 }
        val genders = mutableMapOf<String, Boolean>()
        val infectious = mutableMapOf<String, Boolean>()
        val patientCategories: List<Map<String, Int>> = patientsSimulated.map { day ->
            day.map { patientDistance ->
                val patientId = patientDistance.first.patientId
                val distance = patientDistance.second
                mapOf(patientId to distance)
            }.reduce { acc, map -> acc + map }
        }

        patientsSimulated.forEach { day ->
            day.forEach { patientDistance ->
                genders[patientDistance.first.patientId] = patientDistance.first.gender == "Male"
                infectious[patientDistance.first.patientId] =
                    patientAllocationService.findByPatientId(patientDistance.first)!!.contagious
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

        val solverEndpoint = environmentConfig.getOrDefault("SOLVER_ENDPOINT", "localhost")
        val solverUrl = "http://$solverEndpoint:8000/api/solve-global"
        log.info("Invoking global solver")
        val connection = URI(solverUrl).toURL().openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val jsonBody = jacksonObjectMapper().writeValueAsString(req)

        log.info("Invoking solver with request")
        OutputStreamWriter(connection.outputStream).use {
            it.write(jsonBody)
        }

        if (connection.responseCode != 200) {
            log.warning("Solver returned status code ${connection.responseCode}")
            return "error"
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }

        if (response.contains("Model is unsat")) {
            return "error"
            //return listOf(listOf(mapOf("error" to null)))
        }

        return response
    }

    fun globalSolution(
        needs: SimulationNeeds,
        patients: Map<String, Patient>,
        rooms: List<Room>,
        tempDir: Path,
        smtMode: String
    ): String {
        //List<SimulationResponse> {
        try {
            val solverResponse = invokeGlobal(needs, rooms, smtMode)
            return solverResponse?: "error"
        } catch (e: Exception) {
            "Error executing JAR: ${e.message}"
            log.log(Level.SEVERE, "Error executing JAR", e)
            return "error"
            //return listOf(listOf(listOf(mapOf("error" to null))))
        }
    }
}