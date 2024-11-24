package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

@RestController
@RequestMapping("/api/fuseki")
class FusekiController {

    private val log : Logger = Logger.getLogger(FusekiController::class.java.name)

    @Operation(summary = "Update the model")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Model updated"),
        ApiResponse(responseCode = "400", description = "Invalid model"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/update")
    fun updateModel(@SwaggerRequestBody(description = "Query to update the model") @RequestBody query: String) : ResponseEntity<String> {
        log.info("Updating model")

        return ResponseEntity.ok("Model updated")
    }

    @Operation(summary = "Retrieve the model")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Model retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid model"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun retrieveModel() : ResponseEntity<String> {
        log.info("Retrieving model")

        // Download the bedreflyt.ttl file from here
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)

        // Return the file content with appropriate headers
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"$path\"")
            .body(fileContent)
    }
}