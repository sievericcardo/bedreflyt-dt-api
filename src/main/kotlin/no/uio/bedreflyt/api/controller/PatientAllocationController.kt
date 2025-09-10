package no.uio.bedreflyt.api.controller

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.model.live.PatientAllocation
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.service.live.PatientTrajectoryService
import no.uio.bedreflyt.api.types.PatientAllocationRequest
import no.uio.bedreflyt.api.types.UpdatePatientAllocationRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/patient-allocations")
class PatientAllocationController (
    private val patientService : PatientService,
    private val patientAllocationService : PatientAllocationService,
    private val patientTrajectoryService: PatientTrajectoryService
) {

    private val log : Logger = LoggerFactory.getLogger(PatientAllocationController::class.java.name)

    @Operation(summary = "Create a new patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation created"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces = ["application/json"])
    fun createPatientAllocation(@SwaggerRequestBody(description = "Request to add a new patient allocation") @Valid @RequestBody request: PatientAllocationRequest) : ResponseEntity<String> {
        log.info("Creating patient allocation $request")

        val patient = patientService.findByPatientId(request.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        val patientAllocation = PatientAllocation(
            patientId = patient,
            acute = request.acute,
            diagnosisCode = request.diagnosisCode,
            diagnosisName = request.diagnosisName,
            acuteCategory = request.acuteCategory ?: 0,
            careCategory = request.careCategory ?: 0,
            monitoringCategory = request.monitoringCategory ?: 0,
            careId = request.careId ?: 0,
            contagious = request.contagious,
            roomNumber = request.roomNumber ?: -1
        )

        patientAllocationService.savePatientAllocation(patientAllocation)

        return ResponseEntity.ok("Patient allocation created")
    }

    @Operation(summary = "Get all patient allocations")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocations found"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocations"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces = ["application/json"])
    fun retrievePatientAllocations() : ResponseEntity<List<PatientAllocation>?> {
        log.info("Retrieving all patient allocations")

        val patientAllocations = patientAllocationService.findAll()?.filter { !it.simulated } ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(patientAllocations.filter { it.roomNumber != -1 })
    }

    @Operation(summary = "Get all patient allocations")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocations found"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocations"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/simulated", produces = ["application/json"])
    fun getSimulatedAllocations() : ResponseEntity<List<PatientAllocation>?> {
        log.info("Retrieving all patient allocations")

        val patientAllocations = patientAllocationService.findAllSimulated() ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(patientAllocations.filter { it.roomNumber != -1 })
    }

    @Operation(summary = "Get patient allocation by patientId")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation found"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{patientId}", produces = ["application/json"])
    fun getPatientAllocation(@ApiParam(value = "Request to get a patient allocation by patientId", required = true) @Valid @PathVariable patientId: String) : ResponseEntity<PatientAllocation> {
        log.info("Getting patient allocation")

        if (patientId.isEmpty()) {
            return ResponseEntity.noContent().build()
        }

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.notFound().build()
        val patientAllocation = patientAllocationService.findByPatientId(patient) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(patientAllocation)
    }

    @Operation(summary = "Get allocations for a specific ward and hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Allocations found"),
        ApiResponse(responseCode = "400", description = "Invalid allocations"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getAllocationsForWardAndHospital(@ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                                         @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<List<PatientAllocation>> {
        log.info("Getting allocations for ward $wardName and hospital $hospitalCode")

        val allocations = patientAllocationService.findByWardNameAndHospitalCode(wardName, hospitalCode) ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(allocations.filter { !it.simulated && it.roomNumber != -1 })
    }

    @Operation(summary = "Get allocations for a specific ward and hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Allocations found"),
        ApiResponse(responseCode = "400", description = "Invalid allocations"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/simulated/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getSimulatedAllocationsForWardAndHospital(@ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                                         @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<List<PatientAllocation>> {
        log.info("Getting allocations for ward $wardName and hospital $hospitalCode")

        val allocations = patientAllocationService.findByWardNameAndHospitalCodeSimulated(wardName, hospitalCode) ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(allocations.filter { it.roomNumber != -1 })
    }

    @Operation(summary = "Update a patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation updated"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{patientId}", produces = ["application/json"])
    fun updatePatientAllocation(@ApiParam(value = "Patient id to be update") @Valid @PathVariable patientId: String,
                                @SwaggerRequestBody(description = "Request to update a patient allocation") @Valid @RequestBody updatedPatientAllocation: UpdatePatientAllocationRequest) : ResponseEntity<PatientAllocation> {
        log.info("Updating patient allocation")

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.notFound().build()
        val currentAllocation = patientAllocationService.findByPatientId(patient) ?: return ResponseEntity.notFound().build()
        val patientAllocation = PatientAllocation(
            patientId = patient,
            acute = updatedPatientAllocation.newAcute ?: currentAllocation.acute,
            diagnosisCode = updatedPatientAllocation.newDiagnosisCode ?: currentAllocation.diagnosisCode,
            diagnosisName = updatedPatientAllocation.newDiagnosisName ?: currentAllocation.diagnosisName,
            acuteCategory = updatedPatientAllocation.newAcuteCategory ?: currentAllocation.acuteCategory,
            careCategory = updatedPatientAllocation.newCareCategory ?: currentAllocation.careCategory,
            monitoringCategory = updatedPatientAllocation.newMonitoringCategory ?: currentAllocation.monitoringCategory,
            careId = updatedPatientAllocation.newCareId ?: currentAllocation.careId,
            contagious = updatedPatientAllocation.newContagious ?: currentAllocation.contagious,
            roomNumber = updatedPatientAllocation.newRoomNumber ?: currentAllocation.roomNumber
        )

        patientAllocationService.updatePatientAllocation(patientAllocation)

        return ResponseEntity.ok(patientAllocation)
    }

    @Operation(summary = "Delete a patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation deleted"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{patientId}", produces = ["application/json"])
    fun deletePatientAllocation(@ApiParam(value = "Patient id to be deleted", required = true) @Valid @PathVariable patientId: String) : ResponseEntity<String> {
        log.info("Deleting patient allocation")

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.notFound().build()
        val allocation = patientAllocationService.findByPatientId(patient) ?: return ResponseEntity.notFound().build()
        patientAllocationService.deletePatientAllocation(allocation)

        return ResponseEntity.ok("Patient allocation deleted")
    }

    @Operation(summary = "Delete a patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation deleted"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/all", produces = ["application/json"])
    fun deleteAllPatientAllocation() : ResponseEntity<String> {
        log.info("Deleting patient allocations")

        val allocations = patientAllocationService.findAll() ?: return ResponseEntity.notFound().build()
        if (allocations.isEmpty()) {
            return ResponseEntity.noContent().build()
        }

        for (allocation in allocations) {
            patientAllocationService.deletePatientAllocation(allocation)
        }

        val trajectories = patientTrajectoryService.findAll() ?: return ResponseEntity.ok("Patient allocation deleted")
        if (trajectories.isEmpty()) {
            return ResponseEntity.ok("Patient allocation deleted")
        }

        patientTrajectoryService.deleteSimulatedPatientTrajectories()

//        for (trajectory in trajectories) {
//            patientTrajectoryService.deletePatientTrajectory(trajectory)
//        }

        return ResponseEntity.ok("Patient allocation deleted")
    }
}