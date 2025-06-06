package no.uio.bedreflyt.api.controller

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.types.PatientRequest
import no.uio.bedreflyt.api.types.UpdatePatientRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/patients")
class PatientController (
    private val patientService : PatientService
) {

    private val log : Logger = LoggerFactory.getLogger(HomeController::class.java.name)

    @Operation(summary = "Create a new patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient created"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces = ["application/json"])
    fun createPatient(@SwaggerRequestBody(description = "Request to add a new patient") @Valid @RequestBody patientRequest: PatientRequest) : ResponseEntity<String> {
        log.info("Creating patient $patientRequest")
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        val patient = Patient(
            patientName = patientRequest.patientName,
            patientSurname = patientRequest.patientSurname,
            patientAddress = patientRequest.patientAddress,
            city = patientRequest.city,
            patientBirthdate = patientRequest.patientBirthdate.let { LocalDateTime.parse(it, formatter) },
            gender = patientRequest.gender
        )

        patientService.savePatient(patient)

        return ResponseEntity.ok("Patient created")
    }

    @Operation(summary = "Get patient by patientId")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient found"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{patientId}", produces = ["application/json"])
    fun getPatient(@ApiParam(value = "Request to get a patient by patientId", required = true) @Valid @PathVariable patientId: String) : ResponseEntity<Patient> {
        log.info("Getting patient")

        if (patientId.isEmpty()) {
            return ResponseEntity.noContent().build()
        }

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(patient)
    }

    @Operation(summary = "Get all patients")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "All patients"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces = ["application/json"])
    fun getAllPatients() : ResponseEntity<List<Patient?>> {
        log.info("Getting all patients")
        val patients = patientService.findAll()

        return ResponseEntity.ok(patients)
    }

    @Operation(summary = "Update a patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient updated"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{patientId}", produces = ["application/json"])
    fun updatePatient(@ApiParam(value = "Patient id to update", required = true) @Valid @PathVariable patientId: String,
                      @SwaggerRequestBody(description = "Request to update a patient") @Valid @RequestBody patientRequest: UpdatePatientRequest) : ResponseEntity<Patient> {
        log.info("Updating patient")

        if (patientId.isEmpty()) {
            return ResponseEntity.noContent().build()
        }

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.notFound().build()
        patient.patientName = patientRequest.patientName ?: patient.patientName
        patient.patientSurname = patientRequest.patientSurname ?: patient.patientSurname
        patient.patientAddress = patientRequest.patientAddress ?: patient.patientAddress
        patient.city = patientRequest.city ?: patient.city
        patient.patientBirthdate = patientRequest.patientBirthdate?.let { LocalDateTime.parse(it) } ?: patient.patientBirthdate
        patient.gender = patientRequest.gender ?: patient.gender

        patientService.updatePatient(patient)

        return ResponseEntity.ok(patient)
    }

    @Operation(summary = "Delete a patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient deleted"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{patientId}", produces = ["application/json"])
    fun deletePatient(@ApiParam(value = "Request to delete a patient", required = true) @Valid @PathVariable patientId: String) : ResponseEntity<String> {
        log.info("Deleting patient")

        if (patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient information are required")
        }
        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        patientService.deletePatient(patient)

        return ResponseEntity.ok("Patient deleted")
    }
}