package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.MonitoringCategory
import no.uio.bedreflyt.api.service.triplestore.MonitoringCategoryService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.*

@RestController
@RequestMapping("/api/fuseki/monitoring-category")
class MonitoringCategoryController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val log : Logger = Logger.getLogger(MonitoringCategoryController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category added"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring cateogry"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addRoomCategory(@SwaggerRequestBody(description = "Monitory category to add") @RequestBody request: MonitoringCategoryRequest) : ResponseEntity<MonitoringCategory> {
        log.info("Adding monitoring category")

        if(!monitoringCategoryService.createCategory(request)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok(MonitoringCategory(request.category, request.description))
    }

    @Operation(summary = "Get all rooms")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun getRoomCategories() : ResponseEntity<List<MonitoringCategory>> {
        log.info("Getting rooms")
        val categories = monitoringCategoryService.getAllCategoories() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(categories)
    }

    @Operation(summary = "Update a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category updated"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update")
    fun updateRoomCategory(@SwaggerRequestBody(description = "Request to update a room") @RequestBody request: UpdateMonitoringCategoryRequest) : ResponseEntity<MonitoringCategory> {
        log.info("Updating monitoring category ${request.category}")

        val category = monitoringCategoryService.getCategoryByCategory(request.category) ?: return ResponseEntity.badRequest().build()
        val cat = request.newCategory ?: category.category
        val desc = request.newDescription ?: category.description

        if(!monitoringCategoryService.updateCategory(category, cat, desc)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok(MonitoringCategory(cat, desc))
    }

    @Operation(summary = "Delete a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category deleted"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteRoomCategory(@SwaggerRequestBody(description = "Request to delete a monitoring category") @RequestBody request: DeleteMonitoringCategoryRequest) : ResponseEntity<String> {
        log.info("Deleting monitoring category ${request.category}")

        val category = monitoringCategoryService.getCategoryByCategory(request.category) ?: return ResponseEntity.badRequest().body("Error: the category could not be deleted.")
        if(!monitoringCategoryService.deleteCategory(category)) {
            return ResponseEntity.badRequest().body("Error: the category could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok("Category deleted")
    }
}