package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.service.live.PatientTrajectoryService
import no.uio.bedreflyt.api.service.simulation.DatabaseService
import no.uio.bedreflyt.api.service.triplestore.WardService
import no.uio.bedreflyt.api.utils.Simulator
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.AllocationRequest
import no.uio.bedreflyt.api.types.AllocationResponse
import no.uio.bedreflyt.api.types.DailyNeeds
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/allocation")
class AllocationController (
    private val databaseService: DatabaseService,
    private val simulator: Simulator,
    private val patientAllocationService: PatientAllocationService,
    private val patientService: PatientService,
    private val patientTrajectoryService: PatientTrajectoryService,
    private val wardService: WardService
) {

    private val log: Logger = Logger.getLogger(SimulationController::class.java.name)

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
        log.info("Allocating rooms for ${allocationRequest.scenario.size} patients")

        // Remove expired trajectories
        patientTrajectoryService.deleteExpiredTrajectory()

        val incomingPatients : MutableList<Pair<Patient, String>> = mutableListOf()
        // Ensure to have a valid patient for each scenario, else we won't continue with the allocation
        allocationRequest.scenario.forEach { scenarioRequest ->
            val patient = patientService.findByPatientId(scenarioRequest.patientId) ?: return ResponseEntity.badRequest().build()
            incomingPatients.add(Pair(patient, scenarioRequest.diagnosis))
        }

        incomingPatients.forEach { patient ->
            val patientAllocation = patientAllocationService.findByPatientId(patient.first)

            // If the patient already has an allocation, we won't continue with the allocation
            if (patientAllocation == null) {
                val newPatientAllocation = PatientAllocation(
                    patientId = patient.first,
                    acute = false, // we will need to change this with proper information from the scenario
                    diagnosisCode = patient.second,
                    diagnosisName = patient.second, // we will need a proper naming for this
                    acuteCategory = 0, // we will need to change this with proper information from the scenario
                    careCategory = 0, // we will need to change this with proper information from the scenario
                    monitoringCategory = 0, // we will need to change this with proper information from the scenario
                    careId = 0, // we will need to change this with proper information from the scenario
                    contagious = false, // we will need to change this with proper information from the scenario
                    roomNumber = -1
                )
                patientAllocationService.savePatientAllocation(newPatientAllocation)
            }
        }

        // Create a temporary directory
        val uniqueID = UUID.randomUUID().toString()
        val tempDir: Path = Files.createTempDirectory("allocation_$uniqueID")
        val bedreflytDB = tempDir.resolve("bedreflyt.db").toString()
        databaseService.createTables(bedreflytDB)

        val ward = wardService.getWardByNameAndHospital(allocationRequest.wardName, allocationRequest.hospitalCode) ?: return ResponseEntity.badRequest().build()
        val rooms = databaseService.createAndPopulateRooms(bedreflytDB, ward)
        val patients : MutableMap<String, Patient> = databaseService.createAndPopulatePatientTables(bedreflytDB, allocationRequest.scenario, allocationRequest.mode).toMutableMap()
        // Add the patients that are already in the trajectory
        val trajectories = patientTrajectoryService.findAll() ?: listOf()
        trajectories.forEach { trajectory ->
            val patient = trajectory.patientId
            patients[patient.patientId] = trajectory.patientId
        }

        val allocations : MutableMap<Patient, PatientAllocation> = mutableMapOf()
        patients.forEach { (_, patient) ->
            allocations[patient] = patientAllocationService.findByPatientId(patient)!!
        }

        databaseService.createAndPopulateTreatmentTables(bedreflytDB)
        databaseService.createTreatmentView(bedreflytDB)

        log.info("Tables populated, invoking ABS with ${allocationRequest.scenario.size} requests")

        val simulationNeeds: MutableList<DailyNeeds> = simulator.computeDailyNeeds(tempDir)?.toMutableList() ?: throw Exception("Could not compute daily needs")
        // Add the elements from the trajectory to the simulation needs
        trajectories.forEach { trajectory ->
            log.info("Will be adding ${trajectory.getBatchDay()}")
            simulationNeeds[trajectory.getBatchDay()].add(Pair(trajectory.patientId, trajectory.need))
        }

        // Add all the simulation needs for each day into the trajectory table
        simulationNeeds.forEachIndexed { index, dailyNeeds ->
            dailyNeeds.forEach { (patient, need) ->
                val trajectory = PatientTrajectory(patientId = patient, date = LocalDate.now(), need = need)
                trajectory.date = trajectory.setDate(index)
                patientTrajectoryService.savePatientTrajectory(trajectory)
            }
        }

        val allocationResponse = simulator.simulate(simulationNeeds, patients, allocations, rooms, tempDir, allocationRequest.mode)
        return ResponseEntity.ok(allocationResponse)
    }
}