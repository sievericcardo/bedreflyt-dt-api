package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.MonitoringCategory
import no.uio.bedreflyt.api.service.triplestore.MonitoringCategoryService
import no.uio.bedreflyt.api.types.MonitoringCategoryRequest
import no.uio.bedreflyt.api.types.UpdateMonitoringCategoryRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/monitoring-categories")
class MonitoringCategoryController (
    private val replConfig: REPLConfig,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val log : Logger = Logger.getLogger(MonitoringCategoryController::class.java.name)

    @Operation(summary = "Add a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category added"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring cateogry"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun addMonitoringCategory(@SwaggerRequestBody(description = "Monitory category to add") @Valid @RequestBody request: MonitoringCategoryRequest) : ResponseEntity<MonitoringCategory> {
        log.info("Adding monitoring category")

        if(!monitoringCategoryService.createCategory(request)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok(MonitoringCategory(request.category, request.description))
    }

    @Operation(summary = "Get all monitoring categories")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Category found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun getMonitoringCategories() : ResponseEntity<List<MonitoringCategory>> {
        log.info("Getting monitoring categories")
        val categories = monitoringCategoryService.getAllCategories() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(categories)
    }

    @Operation(summary = "Get a monitoring category by category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Category found"),
        ApiResponse(responseCode = "400", description = "Invalid category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{monitoringCategory}", produces= ["application/json"])
    fun getMonitoringCategory(@ApiParam(value = "Category", required = true) @Valid @PathVariable monitoringCategory: Int) : ResponseEntity<MonitoringCategory> {
        log.info("Getting monitoring category $monitoringCategory")

        val category = monitoringCategoryService.getCategoryByCategory(monitoringCategory) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(category)
    }

    @Operation(summary = "Update a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category updated"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{monitoringCategory}", produces= ["application/json"])
    fun updateMonitoringCategory(@ApiParam(value = "Category", required = true) @Valid @PathVariable monitoringCategory: Int,
                           @SwaggerRequestBody(description = "Request to update a monitoring category") @Valid @RequestBody request: UpdateMonitoringCategoryRequest) : ResponseEntity<MonitoringCategory> {
        log.info("Updating monitoring category $monitoringCategory")

        val category = monitoringCategoryService.getCategoryByCategory(monitoringCategory) ?: return ResponseEntity.notFound().build()
        val desc = request.newDescription ?: category.description

        if(!monitoringCategoryService.updateCategory(category, desc)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok(MonitoringCategory(monitoringCategory, desc))
    }

    @Operation(summary = "Delete a monitoring category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Monitoring category deleted"),
        ApiResponse(responseCode = "400", description = "Invalid monitoring category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{monitoringCategory}", produces= ["application/json"])
    fun deleteMonitoringCategory(@ApiParam(value = "Category", required = true) @Valid @PathVariable monitoringCategory: Int) : ResponseEntity<String> {
        log.info("Deleting monitoring category $monitoringCategory")

        val category = monitoringCategoryService.getCategoryByCategory(monitoringCategory) ?: return ResponseEntity.badRequest().body("Error: the category could not be found.")
        if(!monitoringCategoryService.deleteCategory(category)) {
            return ResponseEntity.badRequest().body("Error: the category could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("monitoring categories")

        return ResponseEntity.ok("Category deleted")
    }
}