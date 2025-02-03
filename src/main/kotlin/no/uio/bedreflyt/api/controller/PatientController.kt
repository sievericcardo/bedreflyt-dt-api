package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.live.PatientService
import no.uio.bedreflyt.api.types.DeletePatientRequest
import no.uio.bedreflyt.api.types.PatientRequest
import no.uio.bedreflyt.api.types.UpdatePatientRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDateTime
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/patient")
class PatientController (
    private val patientService : PatientService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    @Operation(summary = "Create a new patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient created"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createPatient(@SwaggerRequestBody(description = "Request to add a new patient") @RequestBody patientRequest: PatientRequest) : ResponseEntity<String> {
        log.info("Creating patient $patientRequest")

        val patient = Patient(
            patientName = patientRequest.patientName,
            patientSurname = patientRequest.patientSurname,
            patientAddress = patientRequest.patientAddress,
            city = patientRequest.city,
            patientBirthdate = patientRequest.patientBirthdate.let { LocalDateTime.parse(it) },
            gender = patientRequest.gender
        )
        patient.patientId = patient.generatePatientId(patientRequest.patientBirthdate)

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
    @GetMapping("/get/{patientId}")
    fun getPatient(@SwaggerRequestBody(description = "Request to get a patient by patientId") @PathVariable patientId: String) : ResponseEntity<Patient> {
        log.info("Getting patient")

        if (patientId.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val patient = patientService.findByPatientId(patientId) ?: return ResponseEntity.badRequest().build()

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
    @GetMapping("/retrieve")
    fun getAllPatients() : ResponseEntity<HashMap<String, List<Patient>>> {
        log.info("Getting all patients")
        val patients = patientService.findAll()

        val response = HashMap<String, List<Patient>>()
        response["patients"] = patients.toList() as List<Patient>

        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Update a patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient updated"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update")
    fun updatePatient(@SwaggerRequestBody(description = "Request to update a patient") @RequestBody patientRequest: UpdatePatientRequest) : ResponseEntity<String> {
        log.info("Updating patient")

        if (patientRequest.patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient information are required")
        }

        val patient = patientService.findByPatientId(patientRequest.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        patient.patientName = patientRequest.patientName ?: patient.patientName
        patient.patientSurname = patientRequest.patientSurname ?: patient.patientSurname
        patient.patientAddress = patientRequest.patientAddress ?: patient.patientAddress
        patient.city = patientRequest.city ?: patient.city
        patient.patientBirthdate = patientRequest.patientBirthdate?.let { LocalDateTime.parse(it) } ?: patient.patientBirthdate
        patient.gender = patientRequest.gender ?: patient.gender

        patientService.updatePatient(patient)

        return ResponseEntity.ok("Patient updated")
    }

    @Operation(summary = "Delete a patient")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Patient deleted"),
        ApiResponse(responseCode = "400", description = "Invalid patient"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deletePatient(@SwaggerRequestBody(description = "Request to delete a patient") @RequestBody patientRequest: DeletePatientRequest) : ResponseEntity<String> {
        log.info("Deleting patient")

        if (patientRequest.patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient information are required")
        }

        val patient = patientService.findByPatientId(patientRequest.patientId) ?: return ResponseEntity.badRequest().body("Patient not found")
        patientService.deletePatient(patient)

        return ResponseEntity.ok("Patient deleted")
    }
}