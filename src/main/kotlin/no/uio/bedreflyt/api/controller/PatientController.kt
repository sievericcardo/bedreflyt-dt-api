package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.service.live.PatientService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.time.LocalDateTime
import java.util.logging.Logger

data class PatientRequest (
    @NotBlank(message = "Patient ID is required")
    val patientId : String,
    val operationId : String = "",
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}", message = "Invalid date format")
    val operationStart : String? = "",
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}", message = "Invalid date format")
    val operationEnd : String? = "",
    @Min(0, message = "Operation length must be non-negative")
    val operationLengthDays : Float = 0.0f,
    val acute : Boolean = false,
    @NotBlank(message = "Gender is required")
    val gender : String = "",
    @Min(0, message = "Age must be non-negative")
    val age : Int = 0,
    val oslo : Boolean = false,
    @NotBlank(message = "Main diagnosis code is required")
    val mainDiagnosisCode : String = "",
    @NotBlank(message = "Main diagnosis name is required")
    val mainDiagnosisName : String = "",
    val acuteCategory : Int = 0,
    val careCategory : Int = 0,
    val monitoringCategory : Int = 0,
    val postOperationBedtimeHoursCategory : Int = 0,
    val lengthStayDaysCategory : Int = 0,
    val careId : String = "",
    val infectious : Boolean = false,
    val roomNumber: Int = 0
)

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

        if (patientRequest.patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient id is required")
        }

        val patient = Patient(
            patientId = patientRequest.patientId,
            operationId = patientRequest.operationId,
            operationStart = patientRequest.operationStart?.let { LocalDateTime.parse(it) },
            operationEnd = patientRequest.operationEnd?.let { LocalDateTime.parse(it) },
            operationLengthDays = patientRequest.operationLengthDays,
            acute = patientRequest.acute,
            gender = patientRequest.gender,
            age = patientRequest.age,
            oslo = patientRequest.oslo,
            mainDiagnosisCode = patientRequest.mainDiagnosisCode,
            mainDiagnosisName = patientRequest.mainDiagnosisName,
            acuteCategory = patientRequest.acuteCategory,
            careCategory = patientRequest.careCategory,
            monitoringCategory = patientRequest.monitoringCategory,
            postOperationBedtimeHoursCategory = patientRequest.postOperationBedtimeHoursCategory,
            lengthStayDaysCategory = patientRequest.lengthStayDaysCategory,
            careId = patientRequest.careId,
            infectious = patientRequest.infectious,
            roomNumber = patientRequest.roomNumber
        )

        patientService.savePatient(patient)

        return ResponseEntity.ok("Patient created")
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
    fun updatePatient(@SwaggerRequestBody(description = "Request to update a patient") @RequestBody patientRequest: PatientRequest) : ResponseEntity<String> {
        log.info("Updating patient")

        if (patientRequest.patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient information are required")
        }

        val patient = Patient(
            patientId = patientRequest.patientId,
            operationId = patientRequest.operationId,
            operationStart = patientRequest.operationStart?.let { LocalDateTime.parse(it) },
            operationEnd = patientRequest.operationEnd?.let { LocalDateTime.parse(it) },
            operationLengthDays = patientRequest.operationLengthDays,
            acute = patientRequest.acute,
            age = patientRequest.age,
            oslo = patientRequest.oslo,
            mainDiagnosisCode = patientRequest.mainDiagnosisCode,
            mainDiagnosisName = patientRequest.mainDiagnosisName,
            acuteCategory = patientRequest.acuteCategory,
            careCategory = patientRequest.careCategory,
            monitoringCategory = patientRequest.monitoringCategory,
            postOperationBedtimeHoursCategory = patientRequest.postOperationBedtimeHoursCategory,
            lengthStayDaysCategory = patientRequest.lengthStayDaysCategory,
            careId = patientRequest.careId,
            infectious = patientRequest.infectious,
            roomNumber = patientRequest.roomNumber
        )

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
    fun deletePatient(@SwaggerRequestBody(description = "Request to delete a patient") @RequestBody patientRequest: PatientRequest) : ResponseEntity<String> {
        log.info("Deleting patient")

        if (patientRequest.patientId.isEmpty()) {
            return ResponseEntity.badRequest().body("Patient information are required")
        }

        val patients = patientService.findByPatientId(patientRequest.patientId)

        patients.forEach { patient ->
            patientService.deletePatient(patient)
        }

        return ResponseEntity.ok("Patient deleted")
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
    fun getPatient(@SwaggerRequestBody(description = "Request to get a patient by patientId") @PathVariable patientId: String) : ResponseEntity<List<Patient>> {
        log.info("Getting patient")

        if (patientId.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val patient = patientService.findByPatientId(patientId)

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
}