package no.uio.bedreflyt.api.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import no.uio.bedreflyt.api.model.triplestore.TreatmentRoom
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.service.live.PatientTrajectoryService
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.service.triplestore.RoomService
import no.uio.bedreflyt.api.service.triplestore.WardService
import no.uio.bedreflyt.api.types.AllocationContext
import no.uio.bedreflyt.api.utils.Simulator
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import no.uio.bedreflyt.api.types.AllocationRequest
import no.uio.bedreflyt.api.types.AllocationResponse
import no.uio.bedreflyt.api.types.AllocationSetupResult
import no.uio.bedreflyt.api.types.AllocationSimulationRequest
import no.uio.bedreflyt.api.types.CompleteTimeLogging
import no.uio.bedreflyt.api.types.DailyNeeds
import no.uio.bedreflyt.api.types.SimulationRequest
import no.uio.bedreflyt.api.types.SolverTimeLogging
import no.uio.bedreflyt.api.types.DatabaseSetupResult
import no.uio.bedreflyt.api.types.SimulationResult
import no.uio.bedreflyt.api.types.TimeLogging
import no.uio.bedreflyt.api.types.TriggerAllocationRequest
import no.uio.bedreflyt.api.utils.AllocationHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.get
import kotlin.text.contains
import kotlin.text.toLong

@RestController
@RequestMapping("/api/v1/allocation")
class AllocationController (
    private val databaseService: DatabaseService,
    private val simulator: Simulator,
    private val patientAllocationService: PatientAllocationService,
    private val patientService: PatientService,
    private val patientTrajectoryService: PatientTrajectoryService,
    private val wardService: WardService,
    private val roomService: RoomService,
    private val environmentConfig: EnvironmentConfig,
    private val allocationHelper: AllocationHelper
) {

    private val log: Logger = LoggerFactory.getLogger(AllocationController::class.java.name)

    private val roomMap: MutableMap<Int, Int> = mutableMapOf()
    private val indexRoomMap : MutableMap<Int, Int> = mutableMapOf()
    private val roomMapSim: MutableMap<Int, Int> = mutableMapOf()
    private val indexRoomMapSim : MutableMap<Int, Int> = mutableMapOf()

    private val allocationLock: ReentrantLock = ReentrantLock()
    private val simulationLock: ReentrantLock = ReentrantLock()

    @Operation(summary = "Allocate rooms for patients")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Rooms allocated"),
            ApiResponse(responseCode = "400", description = "Invalid allocation"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(
                responseCode = "403",
                description = "Accessing the resource you were trying to reach is forbidden"
            ),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @PostMapping("/allocate")
    fun allocateRooms(@SwaggerRequestBody(description = "Request to allocate rooms for patients") @Valid @RequestBody allocationRequest: AllocationRequest): ResponseEntity<AllocationResponse> {
        allocationLock.lock()
        try {
            log.info("Allocating rooms for ${allocationRequest.scenario.size} patients")
            val startTime = System.currentTimeMillis()

            val context = AllocationContext(
                wardName = allocationRequest.wardName,
                hospitalCode = allocationRequest.hospitalCode,
                isSimulated = false,
                timeStep = 0,
                adaptiveCapacity = allocationRequest.adaptative,
                smtMode = allocationRequest.smtMode
            )

            // Initialize allocation
            val setupResult = initializeAllocation(context, allocationRequest.scenario, startTime)
                ?: return ResponseEntity.badRequest().build()

            // Setup database
            val databaseResult = setupDatabase(context, allocationRequest.scenario, allocationRequest.mode)
                ?: return ResponseEntity.badRequest().build()

            // Create patient allocations
            val allocations = allocationHelper.createPatientAllocations(context, setupResult.incomingPatients)

            // Prepare simulation
            val simulationResult = allocationHelper.prepareSimulation(
                context, setupResult, databaseResult, allocations, allocationRequest.scenario, startTime
            ) ?: return ResponseEntity.badRequest().build()

            // Execute simulation
            allocationHelper.cleanAllocations()
            allocationHelper.removeUnusedAllocations(simulationResult.simulationNeeds[0])
            
            // Add existing allocated patients (roomNumber != -1) and their trajectories
            val existingAllocatedPatients = patientAllocationService.findAll()
                ?.filter { it.wardName == context.wardName && it.hospitalCode == context.hospitalCode }
                ?.filter { it.simulated == context.isSimulated }
                ?.filter { it.roomNumber != -1 }
            
            existingAllocatedPatients?.forEach { allocation ->
                // Add to patient allocations if not already present
                if (!simulationResult.patientAllocations.containsKey(allocation.patientId)) {
                    simulationResult.patientAllocations[allocation.patientId] = allocation
                }
                
                // Add trajectories for these patients
                val trajectories = patientTrajectoryService.findByPatientId(allocation.patientId, context.isSimulated)
                trajectories?.forEach { trajectory ->
                    val batchDay = trajectory.getBatchDay()
                    while (simulationResult.patientsNeeds.size <= batchDay) {
                        simulationResult.patientsNeeds.add(mutableListOf())
                    }
                    if (!simulationResult.patientsNeeds[batchDay].any { it.first == trajectory.patientId }) {
                        simulationResult.patientsNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
                    }
                }
            }
            
            simulator.setRoomMap(roomMap)
            simulator.setIndexRoomMap(indexRoomMap)

            val filteredPatients = simulationResult.patientsNeeds[0].distinctBy { it.first } as DailyNeeds
            val res = simulator.simulate(
                mutableListOf(filteredPatients),
                databaseResult.patients,
                simulationResult.patientAllocations,
                databaseResult.rooms,
                simulationResult.ward,
                databaseResult.tempDir,
                allocationRequest.smtMode
            )
            val allocationResponse = res.first
            val allocationTimes = res.second

            // Process and return response
            return processAllocationResponse(
                allocationResponse, allocationTimes, context, setupResult, 
                simulationResult, setupResult.incomingPatients, startTime
            )
        } finally {
            allocationLock.unlock()
        }
    }

    @Operation(summary = "Allocate rooms for patients")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Rooms allocated"),
            ApiResponse(responseCode = "400", description = "Invalid allocation"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(
                responseCode = "403",
                description = "Accessing the resource you were trying to reach is forbidden"
            ),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @PostMapping("/simulate")
    fun simulateRooms(@SwaggerRequestBody(description = "Request to allocate rooms for patients") @Valid @RequestBody allocationRequest: AllocationSimulationRequest): ResponseEntity<AllocationResponse> {
        simulationLock.lock()
        try {
            log.info("Allocating rooms for ${allocationRequest.scenario.size} patients")
            log.info("Need to remove ${LocalDateTime.now().plusDays(allocationRequest.timeStep)}")
            val startTime = System.currentTimeMillis()

            val context = AllocationContext(
                wardName = allocationRequest.wardName,
                hospitalCode = allocationRequest.hospitalCode,
                isSimulated = true,
                timeStep = allocationRequest.timeStep,
                adaptiveCapacity = allocationRequest.adaptiveCapacity,
                smtMode = allocationRequest.smtMode
            )

            // Initialize allocation
            val setupResult = initializeAllocation(context, allocationRequest.scenario, startTime)
                ?: return ResponseEntity.badRequest().build()

            // Setup database
            val databaseResult = setupDatabase(context, allocationRequest.scenario, allocationRequest.mode)
                ?: return ResponseEntity.badRequest().build()

            // Create patient allocations
            val allocations = allocationHelper.createPatientAllocations(context, setupResult.incomingPatients)

            // Prepare simulation
            val simulationResult = allocationHelper.prepareSimulation(
                context, setupResult, databaseResult, allocations, allocationRequest.scenario, startTime
            ) ?: return ResponseEntity.badRequest().build()

            val now = System.currentTimeMillis()
            val update = System.currentTimeMillis()
            log.info("Updated patient needs in ${update - now}ms")

            val afterNeeds = System.currentTimeMillis()
            log.info("Prepared patient needs in ${afterNeeds - update}ms")

            // Execute simulation
            allocationHelper.cleanAllocations()
            allocationHelper.removeUnusedAllocations(simulationResult.simulationNeeds[0])
            
            // Add existing allocated patients (roomNumber != -1) and their trajectories
            val existingAllocatedPatients = patientAllocationService.findAll()
                ?.filter { it.wardName == context.wardName && it.hospitalCode == context.hospitalCode }
                ?.filter { it.simulated == context.isSimulated }
                ?.filter { it.roomNumber != -1 }
            
            existingAllocatedPatients?.forEach { allocation ->
                // Add to patient allocations if not already present
                if (!simulationResult.patientAllocations.containsKey(allocation.patientId)) {
                    simulationResult.patientAllocations[allocation.patientId] = allocation
                }
                
                // Add trajectories for these patients
                val trajectories = patientTrajectoryService.findByPatientId(allocation.patientId, context.isSimulated)
                trajectories?.forEach { trajectory ->
                    val batchDay = trajectory.getBatchDay()
                    while (simulationResult.patientsNeeds.size <= batchDay) {
                        simulationResult.patientsNeeds.add(mutableListOf())
                    }
                    if (!simulationResult.patientsNeeds[batchDay].any { it.first == trajectory.patientId }) {
                        simulationResult.patientsNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
                    }
                }
            }
            
            simulator.setRoomMap(roomMapSim)
            simulator.setIndexRoomMap(indexRoomMapSim)

            val cleaned = System.currentTimeMillis()
            log.info("Cleaned allocations in ${cleaned - afterNeeds}ms")

            val filteredPatients = simulationResult.patientsNeeds[0].distinctBy { it.first } as DailyNeeds
            val res = simulator.simulate(
                mutableListOf(filteredPatients),
                databaseResult.patients,
                simulationResult.patientAllocations,
                databaseResult.rooms,
                simulationResult.ward,
                databaseResult.tempDir,
                allocationRequest.smtMode
            )
            val allocationResponse = res.first
            val allocationTimes = res.second
            log.info("Allocation response size: ${allocationResponse.allocations.size}")

            // Process and return response
            return processAllocationResponse(
                allocationResponse, allocationTimes, context, setupResult, 
                simulationResult, setupResult.incomingPatients, startTime
            )
        } finally {
            simulationLock.unlock()
        }
    }

    private fun checkRoomOpening (wardName: String, hospitalCode: String, incomingPatients: Int) : TimeLogging? {
        val host = environmentConfig.getOrDefault("LM_HOST", "localhost")
        val port = environmentConfig.getOrDefault("LM_PORT", "8091")
        val endpoint = "http://$host:$port/api/v1/states/check/$wardName/$hospitalCode"

        val requestBody = TriggerAllocationRequest(incomingPatients)

        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            val objectMapper = jacksonObjectMapper()
            val jsonString = objectMapper.writeValueAsString(requestBody)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
        }

        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            // If the status code was 200, I will have to parse the corresponding TimeLoggin passed as a json
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val objectMapper = jacksonObjectMapper()
            objectMapper.readValue(response, TimeLogging::class.java).also {
                log.info("Room opening check successful: $it")
            }
        } else {
            log.warn("API returned status code ${connection.responseCode}")
            null
        }
    }

    private fun createMaps(rooms: List<TreatmentRoom>, simulated: Boolean = false) {
        if (simulated) {
            rooms.forEachIndexed { index, room -> roomMapSim[index] = room.roomNumber }
            roomMapSim.forEach { (key, value) -> indexRoomMapSim[value] = key }
        }
        rooms.forEachIndexed { index, room -> roomMap[index] = room.roomNumber }
        roomMap.forEach { (key, value) -> indexRoomMap[value] = key }
    }

    private fun createTemporaryDatabase(): Pair<Path, String> {
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("allocation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)
        return Pair(tempDir, bedreflytDB)
    }

    private fun preparePatients(allocationRequest: SimulationRequest): MutableList<Pair<Patient, String>> {
        val incomingPatients: MutableList<Pair<Patient, String>> = mutableListOf()
        allocationRequest.scenario.forEach { scenarioRequest ->
            val patient = patientService.findByPatientId(scenarioRequest.patientId) ?: return mutableListOf()
            incomingPatients.add(Pair(patient, scenarioRequest.diagnosis))
        }
        return incomingPatients
    }

    private fun savePatientAllocations(incomingPatients: MutableList<Pair<Patient, String>>) {
        incomingPatients.forEach { patient ->
            val patientAllocation = patientAllocationService.findByPatientId(patient.first)
            patientAllocation?.let {
                if (it.diagnosisCode == "" && it.diagnosisName == "") {
                    it.diagnosisCode = patient.second
                    it.diagnosisName = patient.second
                    patientAllocationService.updatePatientAllocation(it)
                }
            }
        }
    }

    private fun populateDatabase(bedreflytDB: String, allocationRequest: SimulationRequest): Pair<MutableMap<String, Patient>, List<PatientTrajectory>> {
        val ward = wardService.getWardByNameAndHospital(allocationRequest.wardName, allocationRequest.hospitalCode) ?: throw IllegalArgumentException("Invalid ward or hospital")
        databaseService.createAndPopulateRooms(bedreflytDB, ward)
        val patients = databaseService.createAndPopulatePatientTables(bedreflytDB, allocationRequest.scenario, allocationRequest.mode).toMutableMap()
        val trajectories = patientTrajectoryService.findAll() ?: listOf()
        trajectories.forEach { trajectory ->
            val patient = trajectory.patientId
            patients[patient.patientId] = trajectory.patientId
        }
        return Pair(patients, trajectories)
    }

    // Extracted common methods
    private fun initializeAllocation(
        context: AllocationContext,
        scenario: List<no.uio.bedreflyt.api.types.ScenarioRequest>,
        startTime: Long
    ): AllocationSetupResult? {
        // Handle expired trajectories
        if (context.isSimulated) {
            patientTrajectoryService.deleteExpiredTrajectoryWithOffset(context.timeStep)
            patientAllocationService.deletePatientAllocationWithOffset(context.timeStep)
        } else {
            patientTrajectoryService.deleteExpiredTrajectory()
        }

        // Get current patients
        val currentPatients = patientAllocationService.findAll()
            ?.filter { it.wardName == context.wardName && it.hospitalCode == context.hospitalCode }
            ?.filter { it.simulated == context.isSimulated }

        val patientTrajectories = currentPatients?.mapNotNull { patient ->
            patientTrajectoryService.findByPatientId(patient.patientId)
        }?.flatten() ?: listOf()

        // Prepare incoming patients
        val request = if (context.isSimulated) {
            SimulationRequest(
                scenario = scenario,
                mode = "", // Will be set by caller
                smtMode = context.smtMode,
                wardName = context.wardName,
                hospitalCode = context.hospitalCode
            )
        } else {
            AllocationRequest(
                scenario = scenario,
                mode = "", // Will be set by caller
                smtMode = context.smtMode,
                wardName = context.wardName,
                hospitalCode = context.hospitalCode,
                adaptative = context.adaptiveCapacity
            )
        }

        val incomingPatients = preparePatients(request)
        if (incomingPatients.isEmpty()) return null

        // Handle adaptive capacity
        val lmResult = if (context.adaptiveCapacity) {
            checkRoomOpening(context.wardName, context.hospitalCode, incomingPatients.size)
                ?: return null
        } else {
            TimeLogging(0, 0, 0)
        }

        val endLifecycleManager = System.currentTimeMillis() - startTime

        return AllocationSetupResult(
            currentPatients = currentPatients,
            patientTrajectories = patientTrajectories,
            incomingPatients = incomingPatients,
            lmResult = lmResult,
            endLifecycleManager = endLifecycleManager
        )
    }

    private fun setupDatabase(
        context: AllocationContext,
        scenario: List<no.uio.bedreflyt.api.types.ScenarioRequest>,
        mode: String
    ): DatabaseSetupResult? {
        val rooms = roomService.getRoomsByWardHospital(context.wardName, context.hospitalCode)
            ?: return null

        createMaps(rooms, context.isSimulated)

        val (tempDir, bedreflytDB) = createTemporaryDatabase()

        val request = if (context.isSimulated) {
            SimulationRequest(
                scenario = scenario,
                mode = mode,
                smtMode = context.smtMode,
                wardName = context.wardName,
                hospitalCode = context.hospitalCode
            )
        } else {
            AllocationRequest(
                scenario = scenario,
                mode = mode,
                smtMode = context.smtMode,
                wardName = context.wardName,
                hospitalCode = context.hospitalCode,
                adaptative = context.adaptiveCapacity
            )
        }

        val (patients, trajectories) = populateDatabase(bedreflytDB, request)

        return DatabaseSetupResult(
            rooms = rooms,
            tempDir = tempDir,
            bedreflytDB = bedreflytDB,
            patients = patients,
            trajectories = trajectories
        )
    }

    private fun processAllocationResponse(
        allocationResponse: AllocationResponse,
        allocationTimes: SolverTimeLogging,
        context: AllocationContext,
        setupResult: AllocationSetupResult,
        simulationResult: SimulationResult,
        incomingPatients: MutableList<Pair<Patient, String>>,
        startTime: Long
    ): ResponseEntity<AllocationResponse> {
        return if (allocationResponse.allocations.isNotEmpty()) {
            // Save allocation results to database
            allocationResponse.allocations.forEach { allocationList ->
                allocationList.forEach { allocation ->
                    allocation.forEach { (room, roomInfo) ->
                        val allocatedPatients = roomInfo?.patients
                        if (!allocatedPatients.isNullOrEmpty()) {
                            allocatedPatients.forEach { singlePatient ->
                                val patientAllocation = patientAllocationService.findAll()
                                    ?.filter { it.patientId.patientId == singlePatient.patientId }
                                    ?.firstOrNull { it.simulated == context.isSimulated }
                                if (patientAllocation != null) {
                                    if (patientAllocation.roomNumber == -1) {
                                        patientAllocation.roomNumber = room.roomNumber
                                        patientAllocationService.updatePatientAllocation(patientAllocation)
                                    } else {
                                        val roomMapToUse = if (context.isSimulated) roomMapSim else roomMap
                                        patientAllocation.roomNumber =
                                            roomMapToUse[patientAllocation.roomNumber] ?: patientAllocation.roomNumber
                                        patientAllocationService.updatePatientAllocation(patientAllocation)
                                    }
                                } else {
                                    log.warn("Could not find allocation for patient ${singlePatient.patientId}")
                                }
                            }
                        }
                    }
                }
            }

            val endTime = System.currentTimeMillis() - startTime - simulationResult.absTime
            
            // Clean up patients with roomNumber == -1 and their trajectories
            val allocationsToDelete = patientAllocationService.findAll()
                ?.filter { it.wardName == context.wardName && it.hospitalCode == context.hospitalCode }
                ?.filter { it.simulated == context.isSimulated }
                ?.filter { it.roomNumber == -1 }
            
            allocationsToDelete?.forEach { allocation ->
                // Delete trajectories first
                val trajectories = patientTrajectoryService.findByPatientId(allocation.patientId, context.isSimulated)
                trajectories?.forEach { trajectory ->
                    patientTrajectoryService.deletePatientTrajectory(trajectory)
                }
                // Delete allocation
                patientAllocationService.deletePatientAllocation(allocation)
            }
            
            allocationResponse.executions = CompleteTimeLogging(
                lifecycleManagerTime = setupResult.lmResult,
                componentsRetrievalTime = simulationResult.componentsRetrievalTime,
                absTime = simulationResult.absTime,
                solverTime = allocationTimes
            )
            ResponseEntity.ok(allocationResponse)
        } else {
            allocationHelper.handleEmptyAllocations(incomingPatients)
            ResponseEntity.badRequest().build()
        }
    }
}

