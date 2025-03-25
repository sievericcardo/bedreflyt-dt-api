package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.service.triplestore.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.TreatmentRequest
import no.uio.bedreflyt.api.types.UpdateTreatmentRequest
import org.springframework.web.bind.annotation.PathVariable

@RestController
@RequestMapping("/api/v1/fuseki/treatments")
class TreatmentController (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val triplestoreService: TriplestoreService,
    private val diagnosisService: DiagnosisService,
    private val treatmentService: TreatmentService,
) {

    private val log: Logger = Logger.getLogger(TreatmentController::class.java.name)
    private val ttlPrefix = triplestoreProperties.ttlPrefix

    @Operation(summary = "Create a new treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment created"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping
    fun createTreatment(@SwaggerRequestBody(description = "Request to create a new treatment") @RequestBody request: TreatmentRequest) : ResponseEntity<Treatment> {
        log.info("Creating treatment $request")

        val diagnosis = diagnosisService.getDiagnosisByName(request.diagnosis) ?: return ResponseEntity.badRequest().build()
        val firstTask = treatmentService.getTreatmentStep(request.firstStep, request.treatmentName) ?: return ResponseEntity.badRequest().build()
        val lastTask = treatmentService.getTreatmentStep(request.lastStep, request.treatmentName) ?: return ResponseEntity.badRequest().build()

        if (!treatmentService.createTreatment(request)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("treatments")

        return ResponseEntity.ok(Treatment(request.treatmentName, request.treatmentDescription, diagnosis, request.frequency, request.weight, firstTask, lastTask))
    }

    @Operation(summary = "Get all treatments")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "All treatments returned"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping
    fun getAllTreatments() : ResponseEntity<List<Treatment>> {
        log.info("Getting all treatments")

        val treatments = treatmentService.getAllTreatments() ?: return ResponseEntity.noContent().build()
        val treatmentList = treatments.map { it.first }
        return ResponseEntity.ok(treatmentList)
    }

    @Operation(summary = "Get a treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment found"),
        ApiResponse(responseCode = "400", description = "No treatment found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{treatmentName}")
    fun getTreatment(@SwaggerRequestBody(description = "Request to retrieve a treatment by name") @RequestBody request: DeleteTreatmentRequest) : ResponseEntity<Treatment> {
        log.info("Getting treatment $request")

        val treatment = treatmentService.getTreatmentsByTreamentName(request.treatmentName) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(treatment.first)
    }

    @Operation(summary = "Update a treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment updated"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{treatmentName}")
    fun updateTreatment(@ApiParam(value = "Treatment name", required = true) @PathVariable treatmentName: String,
                        @SwaggerRequestBody(description = "Request to update a treatment") @RequestBody request: UpdateTreatmentRequest) : ResponseEntity<Treatment> {
        log.info("Updating treatment $treatmentName")

        return ResponseEntity.badRequest().build()
    }

    @Operation(summary = "Delete a treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment deleted"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping
    fun deleteTreatment(@ApiParam(value = "Treatment name", required = true) @PathVariable treatmentName: String) : ResponseEntity<String> {
        log.info("Deleting treatment $treatmentName")

        if (treatmentService.getTreatmentStepsByTreatmentName(treatmentName) == null) {
            return ResponseEntity.notFound().build()
        }
        if (!treatmentService.deleteTreatment(treatmentName)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("treatments")

        return ResponseEntity.ok("Treatment $treatmentName deleted")
    }
}