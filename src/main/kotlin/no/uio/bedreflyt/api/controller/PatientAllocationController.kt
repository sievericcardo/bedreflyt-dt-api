package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.model.live.PatientAllocation
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.types.PatientAllocationRequest
import no.uio.bedreflyt.api.types.UpdatePatientAllocationRequest
import no.uio.bedreflyt.api.types.DeletePatientAllocationRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

@RestController
@RequestMapping("/api/patient-allocation")
class PatientAllocationController (
    private val patientService : PatientService,
    private val patientAllocationService : PatientAllocationService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    @Operation(summary = "Create a new patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation created"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createPatientAllocation(@SwaggerRequestBody(description = "Request to add a new patient allocation") @RequestBody patientAllocation: PatientAllocationRequest) : ResponseEntity<String> {
        log.info("Creating patient allocation $patientAllocation")

        val patient = patientService.findByPatientId(patientAllocation.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        val patientAllocation = PatientAllocation(
            patientId = patient,
            acute = patientAllocation.acute,
            diagnosisCode = patientAllocation.diagnosisCode,
            diagnosisName = patientAllocation.diagnosisName,
            acuteCategory = patientAllocation.acuteCategory ?: 0,
            careCategory = patientAllocation.careCategory ?: 0,
            monitoringCategory = patientAllocation.monitoringCategory ?: 0,
            careId = patientAllocation.careId ?: 0,
            contagious = patientAllocation.contagious,
            roomNumber = patientAllocation.roomNumber ?: -1
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
    @GetMapping("/retrieve")
    fun retrievePatientAllocations() : ResponseEntity<List<PatientAllocation>?> {
        log.info("Retrieving all patient allocations")

        val patientAllocations = patientAllocationService.findAll()

        return ResponseEntity.ok(patientAllocations)
    }

    @Operation(summary = "Get patient allocation by patientId")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation found"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/get/{patientId}")
    fun getPatientAllocation(@SwaggerRequestBody(description = "Request to get a patient allocation by patientId") @PathVariable patientId: String) : ResponseEntity<PatientAllocation> {
        log.info("Getting patient allocation")

        if (patientId.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.badRequest().build()
        val patientAllocation = patientAllocationService.findByPatientId(patient)

        return ResponseEntity.ok(patientAllocation)
    }

    @Operation(summary = "Update a patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation updated"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PutMapping("/update")
    fun updatePatientAllocation(@SwaggerRequestBody(description = "Request to update a patient allocation") @RequestBody updatedPatientAllocation: UpdatePatientAllocationRequest) : ResponseEntity<String> {
        log.info("Updating patient allocation")

        val patient = patientService.findByPatientId(updatedPatientAllocation.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        val currentAllocation = patientAllocationService.findByPatientId(patient) ?: return ResponseEntity.badRequest().body("Patient allocation not found")
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

        return ResponseEntity.ok("Patient allocation updated")
    }

    @Operation(summary = "Delete a patient allocation")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient allocation deleted"),
        ApiResponse(responseCode = "400", description = "Invalid patient allocation"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deletePatientAllocation(@SwaggerRequestBody(description = "Request to delete a patient allocation") @RequestBody patientAllocation: DeletePatientAllocationRequest) : ResponseEntity<String> {
        log.info("Deleting patient allocation")

        val patient = patientService.findByPatientId(patientAllocation.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        val allocation = patientAllocationService.findByPatientId(patient) ?: return ResponseEntity.badRequest().body("Patient allocation not found")
        patientAllocationService.deletePatientAllocation(allocation)

        return ResponseEntity.ok("Patient allocation deleted")
    }
}