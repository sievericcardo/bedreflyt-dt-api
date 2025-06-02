package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
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
import no.uio.bedreflyt.api.utils.Simulator
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import no.uio.bedreflyt.api.types.AllocationRequest
import no.uio.bedreflyt.api.types.AllocationResponse
import no.uio.bedreflyt.api.types.AllocationSimulationRequest
import no.uio.bedreflyt.api.types.DailyNeeds
import no.uio.bedreflyt.api.types.SimulationRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.locks.ReentrantLock

@RestController
@RequestMapping("/api/v1/allocation")
class AllocationController (
    private val databaseService: DatabaseService,
    private val simulator: Simulator,
    private val patientAllocationService: PatientAllocationService,
    private val patientService: PatientService,
    private val patientTrajectoryService: PatientTrajectoryService,
    private val wardService: WardService,
    private val roomService: RoomService
) {

    private val log: Logger = LoggerFactory.getLogger(AllocationController::class.java.name)
    
    private val roomMap: MutableMap<Int, Int> = mutableMapOf()
    private val indexRoomMap : MutableMap<Int, Int> = mutableMapOf()

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

            // Remove expired trajectories
            patientTrajectoryService.deleteExpiredTrajectory()

            val currentPatients = patientAllocationService.findAll()
                ?.filter { it.wardName == allocationRequest.wardName && it.hospitalCode == allocationRequest.hospitalCode }
                ?.filter { !it.simulated }
            val patientTrajectories = currentPatients?.mapNotNull { patient ->
                patientTrajectoryService.findByPatientId(patient.patientId)
            }?.flatten() ?: listOf()

            val incomingPatients = preparePatients(allocationRequest)
            if (incomingPatients.isEmpty()) return ResponseEntity.badRequest().build()
            val rooms = roomService.getRoomsByWardHospital(allocationRequest.wardName, allocationRequest.hospitalCode)
                ?: return ResponseEntity.badRequest().build()
            createMaps(rooms)

            val (tempDir, bedreflytDB) = createTemporaryDatabase()
            val (patients, trajectories) = populateDatabase(bedreflytDB, allocationRequest)

            val allocations: MutableMap<Patient, PatientAllocation> = mutableMapOf()
            incomingPatients.forEach { (patient, diagnosis) ->
                val patientAllocation = patientAllocationService.findByPatientId(patient)
                if (patientAllocation == null) {
                    val newPatientAllocation = PatientAllocation(
                        patientId = patient,
                        acute = false, // Set appropriate values as needed
                        diagnosisCode = diagnosis,
                        diagnosisName = diagnosis,
                        acuteCategory = 0,
                        careCategory = 0,
                        monitoringCategory = 0,
                        careId = 0,
                        contagious = false,
                        wardName = allocationRequest.wardName,
                        hospitalCode = allocationRequest.hospitalCode,
                        roomNumber = -1,
                        dueDate = LocalDateTime.now().plusDays(1),
                        simulated = false
                    )
                    patientAllocationService.savePatientAllocation(newPatientAllocation)
                    allocations[patient] = newPatientAllocation
                } else {
                    allocations[patient] = patientAllocation
                }
            }

            databaseService.createAndPopulateTreatmentTables(bedreflytDB)
            databaseService.createTreatmentView(bedreflytDB)

            log.info("Tables populated, invoking ABS with ${allocationRequest.scenario.size} requests")

            val simulationNeeds: MutableList<DailyNeeds> = simulator.computeDailyNeeds(tempDir)?.toMutableList()
                ?: throw Exception("Could not compute daily needs")
            val patientNeeds = mutableMapOf<Patient, Long>()
            updatePatientNeeds(simulationNeeds, trajectories, patientNeeds, false)
            updateAllocations(patientNeeds, 0, false)
            val ward = wardService.getWardByNameAndHospital(allocationRequest.wardName, allocationRequest.hospitalCode)
                ?: return ResponseEntity.badRequest().build()

            // Create a complete allocations with both allocations and the addition of the currentPatients
            val patientAllocations = mutableMapOf<Patient, PatientAllocation>()
            currentPatients?.forEach { patient ->
                val patientAllocation = patientAllocationService.findByPatientId(patient.patientId)
                if (patientAllocation != null) {
                    patientAllocations[patient.patientId] = patientAllocation
                }
            }
            allocations.forEach { (patient, allocation) ->
                patientAllocations[patient] = allocation
            }

            val patientsNeeds = mutableListOf<DailyNeeds>()
            simulationNeeds.forEach { dailyNeeds ->
                patientsNeeds.add(dailyNeeds)
            }
            patientTrajectories.forEach { trajectory ->
                val batchDay = trajectory.getBatchDay()
                while (patientsNeeds.size <= batchDay) {
                    patientsNeeds.add(mutableListOf())
                }
                if (!patientsNeeds[batchDay].any { it.first == trajectory.patientId }) {
                    patientsNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
                }
            }

            cleanAllocations()
            simulator.setRoomMap(roomMap)
            simulator.setIndexRoomMap(indexRoomMap)
            val allocationResponse = simulator.simulate(
                patientsNeeds,
                patients,
                patientAllocations,
                rooms,
                ward,
                tempDir,
                allocationRequest.smtMode
            )
//        if (allocationResponse.allocations.isEmpty()) {
//            val otherWards = wardService.getAllWardsExcept(allocationRequest.wardName, allocationRequest.hospitalCode)!!
//            for (otherWard in otherWards) {
//                val otherRooms = roomService.getRoomsByWardHospital(otherWard.wardName, otherWard.wardHospital.hospitalCode)!!
//                val allocationRoom = mutableListOf<TreatmentRoom>()
//                otherRooms.forEach { otherRoom ->
//                    allocationRoom.add(TreatmentRoom(otherRoom.roomNumber, otherRoom.capacity, otherWard, otherWard.wardHospital, otherRoom.monitoringCategory))
//                }
//                allocationResponse = simulator.simulate(simulationNeeds, patients, allocations, allocationRoom, otherWard, tempDir, allocationRequest.mode)
//                if (allocationResponse.allocations.isNotEmpty()) {
//                    break
//                }
//            }
//        }

            return if (allocationResponse.allocations.isNotEmpty()) {
                // if the allocation is not empty, we will save the first allocation to the database
                allocationResponse.allocations.forEach { allocationList ->
                    allocationList.forEach { allocation ->
                        allocation.forEach { (room, roomInfo) ->
                            val allocatedPatients = roomInfo?.patients
                            if (!allocatedPatients.isNullOrEmpty()) {
                                allocatedPatients.forEach { singlePatient ->
                                    val patientAllocation = patientAllocationService.findAll()
                                        ?.filter { it.patientId.patientId == singlePatient.patientId }
                                        ?.firstOrNull { !it.simulated }
                                    if (patientAllocation != null) {
                                        if (patientAllocation.roomNumber == -1) {
                                            patientAllocation.roomNumber = room.roomNumber
                                            patientAllocationService.updatePatientAllocation(patientAllocation)
                                        } else {
                                            patientAllocation.roomNumber =
                                                roomMap[patientAllocation.roomNumber] ?: patientAllocation.roomNumber
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
                ResponseEntity.ok(allocationResponse)
            } else {
                handleEmptyAllocations(incomingPatients)
                ResponseEntity.badRequest().build()
            }
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
            log.info("Need to remove ${LocalDateTime.now().plusDays(allocationRequest.iteration)}")

            // Remove expired trajectories with offset
            patientTrajectoryService.deleteExpiredTrajectoryWithOffset(allocationRequest.iteration)
            // Remove expired allocations with offset
            patientAllocationService.deletePatientAllocationWithOffset(allocationRequest.iteration)

            val request = SimulationRequest(
                scenario = allocationRequest.scenario,
                mode = allocationRequest.mode,
                smtMode = allocationRequest.smtMode,
                wardName = allocationRequest.wardName,
                hospitalCode = allocationRequest.hospitalCode
            )

            val currentPatients = patientAllocationService.findAll()
                ?.filter { it.wardName == allocationRequest.wardName && it.hospitalCode == allocationRequest.hospitalCode }
                ?.filter { it.simulated }
            val patientTrajectories = currentPatients?.mapNotNull { patient ->
                patientTrajectoryService.findByPatientId(patient.patientId)
            }?.flatten() ?: listOf()

            val incomingPatients = preparePatients(request)
            if (incomingPatients.isEmpty()) return ResponseEntity.badRequest().build()
            val rooms = roomService.getRoomsByWardHospital(allocationRequest.wardName, allocationRequest.hospitalCode)
                ?: return ResponseEntity.badRequest().build()
            createMaps(rooms)

            val (tempDir, bedreflytDB) = createTemporaryDatabase()
            val (patients, trajectories) = populateDatabase(bedreflytDB, request)

            val allocations: MutableMap<Patient, PatientAllocation> = mutableMapOf()
            incomingPatients.forEach { (patient, diagnosis) ->
                val patientAllocation = patientAllocationService.findByPatientId(patient)
                if (patientAllocation == null) {
                    val newPatientAllocation = PatientAllocation(
                        patientId = patient,
                        acute = false, // Set appropriate values as needed
                        diagnosisCode = diagnosis,
                        diagnosisName = diagnosis,
                        acuteCategory = 0,
                        careCategory = 0,
                        monitoringCategory = 0,
                        careId = 0,
                        contagious = false,
                        wardName = allocationRequest.wardName,
                        hospitalCode = allocationRequest.hospitalCode,
                        roomNumber = -1,
                        dueDate = LocalDateTime.now().plusDays(1),
                        simulated = true
                    )
                    patientAllocationService.savePatientAllocation(newPatientAllocation)
                    allocations[patient] = newPatientAllocation
                } else {
                    allocations[patient] = patientAllocation
                }
            }

            databaseService.createAndPopulateTreatmentTables(bedreflytDB, true)
            databaseService.createTreatmentView(bedreflytDB)

            log.info("Tables populated, invoking ABS with ${allocationRequest.scenario.size} requests")

            val simulationNeeds: MutableList<DailyNeeds> = simulator.computeDailyNeeds(tempDir)?.toMutableList()
                ?: throw Exception("Could not compute daily needs")
            val patientNeeds = mutableMapOf<Patient, Long>()
            updatePatientNeeds(simulationNeeds, trajectories, patientNeeds, true)
            updateAllocations(patientNeeds, allocationRequest.iteration, true)
            val ward = wardService.getWardByNameAndHospital(allocationRequest.wardName, allocationRequest.hospitalCode)
                ?: return ResponseEntity.badRequest().build()

            // Create a complete allocations with both allocations and the addition of the currentPatients
            val patientAllocations = mutableMapOf<Patient, PatientAllocation>()
            currentPatients?.forEach { patient ->
                val patientAllocation = patientAllocationService.findByPatientId(patient.patientId)
                if (patientAllocation != null) {
                    patientAllocations[patient.patientId] = patientAllocation
                }
            }
            allocations.forEach { (patient, allocation) ->
                patientAllocations[patient] = allocation
            }

            val patientsNeeds = mutableListOf<DailyNeeds>()
            simulationNeeds.forEach { dailyNeeds ->
                patientsNeeds.add(dailyNeeds)
            }
            patientTrajectories.forEach { trajectory ->
                val batchDay = trajectory.getBatchDay()
                while (patientsNeeds.size <= batchDay) {
                    patientsNeeds.add(mutableListOf())
                }
                if (!patientsNeeds[batchDay].any { it.first == trajectory.patientId }) {
                    patientsNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
                }
            }

            cleanAllocations()
            simulator.setRoomMap(roomMap)
            simulator.setIndexRoomMap(indexRoomMap)
            val allocationResponse = simulator.simulate(
                patientsNeeds,
                patients,
                patientAllocations,
                rooms,
                ward,
                tempDir,
                allocationRequest.smtMode
            )
            log.info("Allocation response size: ${allocationResponse.allocations.size}")
//        if (allocationResponse.allocations.isEmpty()) {
//            val otherWards = wardService.getAllWardsExcept(allocationRequest.wardName, allocationRequest.hospitalCode)!!
//            for (otherWard in otherWards) {
//                val otherRooms = roomService.getRoomsByWardHospital(otherWard.wardName, otherWard.wardHospital.hospitalCode)!!
//                val allocationRoom = mutableListOf<TreatmentRoom>()
//                otherRooms.forEach { otherRoom ->
//                    allocationRoom.add(TreatmentRoom(otherRoom.roomNumber, otherRoom.capacity, otherWard, otherWard.wardHospital, otherRoom.monitoringCategory))
//                }
//                allocationResponse = simulator.simulate(simulationNeeds, patients, allocations, allocationRoom, otherWard, tempDir, allocationRequest.mode)
//                if (allocationResponse.allocations.isNotEmpty()) {
//                    break
//                }
//            }
//        }

            return if (allocationResponse.allocations.isNotEmpty()) {
                // if the allocation is not empty, we will save the first allocation to the database
                allocationResponse.allocations.forEach { allocationList ->
                    allocationList.forEach { allocation ->
                        allocation.forEach { (room, roomInfo) ->
                            val allocatedPatients = roomInfo?.patients
                            if (!allocatedPatients.isNullOrEmpty()) {
                                allocatedPatients.forEach { singlePatient ->
                                    val patientAllocation = patientAllocationService.findAll()
                                        ?.filter { it.patientId.patientId == singlePatient.patientId }
                                        ?.firstOrNull { it.simulated }
                                    if (patientAllocation != null) {
                                        if (patientAllocation.roomNumber == -1) {
                                            patientAllocation.roomNumber = room.roomNumber
                                            patientAllocationService.updatePatientAllocation(patientAllocation)
                                        } else {
                                            patientAllocation.roomNumber =
                                                roomMap[patientAllocation.roomNumber] ?: patientAllocation.roomNumber
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
                ResponseEntity.ok(allocationResponse)
            } else {
                handleEmptyAllocations(incomingPatients)
                ResponseEntity.badRequest().build()
            }
        } finally {
            simulationLock.unlock()
        }
    }

    private fun createMaps(rooms: List<TreatmentRoom>) {
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

    private fun preparePatients(allocationRequest: AllocationRequest): MutableList<Pair<Patient, String>> {
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

    private fun populateDatabase(bedreflytDB: String, allocationRequest: AllocationRequest): Pair<MutableMap<String, Patient>, List<PatientTrajectory>> {
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

    private fun updatePatientNeeds(simulationNeeds: MutableList<DailyNeeds>, trajectories: List<PatientTrajectory>, patientNeeds: MutableMap<Patient, Long>, simulation: Boolean) {
        trajectories.forEach { trajectory ->
            val batchDay = trajectory.getBatchDay()
            while (simulationNeeds.size <= batchDay) {
                simulationNeeds.add(mutableListOf())
            }
            if (!simulationNeeds[batchDay].any { it.first == trajectory.patientId }) {
                simulationNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
            }
        }

        simulationNeeds.forEachIndexed { index, dailyNeeds ->
            dailyNeeds.forEach { (patient, need) ->
                patientNeeds[patient] = patientNeeds.getOrDefault(patient, 0L) + need.toLong()
                val trajectory = PatientTrajectory(patientId = patient, date = LocalDateTime.now(), need = need, simulated =  simulation)
                if (simulation) {
                    trajectory.date = trajectory.setDate(index*24)
                } else {
                    trajectory.date = trajectory.setDate(index)
                }
                patientTrajectoryService.savePatientTrajectory(trajectory)
            }
        }
    }

    private fun updateAllocations(patientNeeds: Map<Patient, Long>, offset:Long = 0, simulation: Boolean) {
        patientNeeds.forEach { (patient, need) ->
            val allocation = patientAllocationService.findByPatientId(patient, simulation)
            allocation?.dueDate = LocalDateTime.now().plusDays((need/24).toInt()+offset)
            allocation?.let { patientAllocationService.updatePatientAllocation(it) }
        }
    }

    private fun handleEmptyAllocations(incomingPatients: MutableList<Pair<Patient, String>>) {
        incomingPatients.forEach { patient ->
            val patientTrajectory = patientTrajectoryService.findByPatientId(patient.first)
            patientTrajectory?.forEach { trajectory ->
                patientTrajectoryService.deletePatientTrajectory(trajectory)
            }
            val patientAllocation = patientAllocationService.findByPatientId(patient.first)
            if (patientAllocation != null) {
                patientAllocationService.deletePatientAllocation(patientAllocation)
            }
        }
    }

    private fun cleanAllocations() {
        val allocation = patientAllocationService.findAll()
        allocation?.forEach { patientAllocation ->
            if (patientAllocation.diagnosisCode == "" && patientAllocation.diagnosisName == "") {
                cleanTrajectories(patientAllocation.patientId)
                patientAllocationService.deletePatientAllocation(patientAllocation)
            }
        }
    }

    private fun cleanTrajectories(patientId: Patient) {
        val trajectories = patientTrajectoryService.findByPatientId(patientId)
        trajectories?.forEach { trajectory ->
            patientTrajectoryService.deletePatientTrajectory(trajectory)
        }
    }
}