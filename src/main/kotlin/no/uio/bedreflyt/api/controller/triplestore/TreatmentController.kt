package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.service.triplestore.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

data class TreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val frequency: Double,
    val weight: Double
)

data class UpdateTreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val oldFrequency: Double,
    val oldWeight: Double,
    val newFrequency: Double,
    val newWeight: Double
)

data class DeteleTreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val frequency: Double,
    val weight: Double
)

@RestController
@RequestMapping("/api/fuseki/treatment")
class TreatmentController (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val triplestoreService: TriplestoreService,
    private val diagnosisService: DiagnosisService,
    private val treatmentService: TreatmentService
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
    @PostMapping("/create")
    fun createTreatment(@SwaggerRequestBody(description = "Request to add a new treatment") @RequestBody treatmentRequest: TreatmentRequest) : ResponseEntity<String> {
        // Check if diagnosis exists
        if (diagnosisService.getDiagnosisByName(treatmentRequest.diagnosis) == null) {
            log.warning("Diagnosis ${treatmentRequest.diagnosis} does not exist")
            if (!diagnosisService.createDiagnosis(treatmentRequest.diagnosis)) {
                log.warning("Failed to create diagnosis ${treatmentRequest.diagnosis}")
                return ResponseEntity.badRequest().body("Failed to create diagnosis ${treatmentRequest.diagnosis}")
            }
        }

        if (!treatmentService.createTreatment(treatmentRequest.treatmentId, treatmentRequest.diagnosis, treatmentRequest.frequency, treatmentRequest.weight)) {
            log.warning("Failed to create treatment ${treatmentRequest.treatmentId}")
            return ResponseEntity.badRequest().body("Failed to create treatment ${treatmentRequest.treatmentId}")
        }

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
                $fileContent
                    
                ###  $ttlPrefix/treatment_${treatmentRequest.treatmentId}_${treatmentRequest.diagnosis}
                :treatment_${treatmentRequest.treatmentId}_${treatmentRequest.diagnosis} rdf:type owl:NamedIndividual ,
                                :Treatment ;
                    :treatmentId "${treatmentRequest.treatmentId}" ;
                    :diagnosis "${treatmentRequest.diagnosis}" ;
                    :frequency "${treatmentRequest.frequency}"^^xsd:double ;
                    :weight "${treatmentRequest.weight}"^^xsd:double .
                """.trimIndent()

        File(path).writeText(newContent)
        replConfig.regenerateSingleModel().invoke("treatments")

        return ResponseEntity.ok("Treatment ${treatmentRequest.treatmentId} created")
    }

    @Operation(summary = "Get all treatments")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "List of treatments"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/all")
    fun getAllTreatments() : ResponseEntity<List<Any>> {
        val treatments = treatmentService.getAllTreatments()
        if (treatments == null) {
            log.warning("No treatments found")
            return ResponseEntity.badRequest().body(listOf("No treatments found"))
        }

        return ResponseEntity.ok(treatments)
    }

    @Operation(summary = "Update an existing treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment updated"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update")
    fun updateTreatment(@SwaggerRequestBody(description = "Request to update a treatment" ) @RequestBody updateTreatmentRequest: UpdateTreatmentRequest) : ResponseEntity<String> {
        // Check if treatment exists
        if (treatmentService.getTreatmentById(updateTreatmentRequest.treatmentId) == null) {
            log.warning("Treatment ${updateTreatmentRequest.treatmentId} does not exist")
            return ResponseEntity.badRequest().body("Treatment ${updateTreatmentRequest.treatmentId} does not exist")
        }

        if (!treatmentService.updateTreatment(updateTreatmentRequest.treatmentId, updateTreatmentRequest.diagnosis, updateTreatmentRequest.oldFrequency, updateTreatmentRequest.oldWeight, updateTreatmentRequest.newFrequency, updateTreatmentRequest.newWeight)) {
            log.warning("Failed to update treatment ${updateTreatmentRequest.treatmentId}")
            return ResponseEntity.badRequest().body("Failed to update treatment ${updateTreatmentRequest.treatmentId}")
        }

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
                ###  $ttlPrefix/treatment_${updateTreatmentRequest.treatmentId}_${updateTreatmentRequest.diagnosis}
                :treatment_${updateTreatmentRequest.treatmentId}_${updateTreatmentRequest.diagnosis} rdf:type owl:NamedIndividual ,
                                :Treatment ;
                    :treatmentId "${updateTreatmentRequest.treatmentId}" ;
                    :diagnosis "${updateTreatmentRequest.diagnosis}" ;
                    :frequency "${updateTreatmentRequest.oldFrequency}"^^xsd:double ;
                    :weight "${updateTreatmentRequest.oldWeight}"^^xsd:double .
                """.trimIndent()
        val newContent = """
                ###  $ttlPrefix/treatment_${updateTreatmentRequest.treatmentId}_${updateTreatmentRequest.diagnosis}
                :treatment_${updateTreatmentRequest.treatmentId}_${updateTreatmentRequest.diagnosis} rdf:type owl:NamedIndividual ,
                                :Treatment ;
                    :treatmentId "${updateTreatmentRequest.treatmentId}" ;
                    :diagnosis "${updateTreatmentRequest.diagnosis}" ;
                    :frequency "${updateTreatmentRequest.newFrequency}"^^xsd:double ;
                    :weight "${updateTreatmentRequest.newWeight}"^^xsd:double .
                """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)
        replConfig.regenerateSingleModel().invoke("treatments")

        return ResponseEntity.ok("Treatment ${updateTreatmentRequest.treatmentId} updated")
    }

    @Operation(summary = "Delete an existing treatment")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment deleted"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteTreatment(@SwaggerRequestBody(description = "Request to delete a treatment") @RequestBody deleteTreatmentRequest: DeteleTreatmentRequest) : ResponseEntity<String> {
        // Check if treatment exists
        if (treatmentService.getTreatmentById(deleteTreatmentRequest.treatmentId) == null) {
            log.warning("Treatment ${deleteTreatmentRequest.treatmentId} does not exist")
            return ResponseEntity.badRequest().body("Treatment ${deleteTreatmentRequest.treatmentId} does not exist")
        }

        if (!treatmentService.deleteTreatment(deleteTreatmentRequest.treatmentId, deleteTreatmentRequest.diagnosis)) {
            log.warning("Failed to delete treatment ${deleteTreatmentRequest.treatmentId}")
            return ResponseEntity.badRequest().body("Failed to delete treatment ${deleteTreatmentRequest.treatmentId}")
        }

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val content = """
                ###  $ttlPrefix/treatment_${deleteTreatmentRequest.treatmentId}_${deleteTreatmentRequest.diagnosis}
                :treatment_${deleteTreatmentRequest.treatmentId}_${deleteTreatmentRequest.diagnosis} rdf:type owl:NamedIndividual ,
                                :Treatment ;
                    :treatmentId "${deleteTreatmentRequest.treatmentId}" ;
                    :diagnosis "${deleteTreatmentRequest.diagnosis}" ;
                    :frequency "${deleteTreatmentRequest.frequency}"^^xsd:double ;
                    :weight "${deleteTreatmentRequest.weight}"^^xsd:double .
                """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, content, "")
        replConfig.regenerateSingleModel().invoke("treatments")

        return ResponseEntity.ok("Treatment ${deleteTreatmentRequest.treatmentId} deleted")
    }
}