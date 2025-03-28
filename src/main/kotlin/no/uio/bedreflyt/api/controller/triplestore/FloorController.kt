package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Floor
import no.uio.bedreflyt.api.service.triplestore.FloorService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import no.uio.bedreflyt.api.types.FloorRequest
import no.uio.bedreflyt.api.types.UpdateFloorRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/floors")
class FloorController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val floorService: FloorService
) {

    private val log: Logger = Logger.getLogger(FloorController::class.java.name)

    @Operation(summary = "Add a new floor")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Floor added"),
        ApiResponse(responseCode = "400", description = "Invalid floor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createFloor(@SwaggerRequestBody(description = "Request to add a new floor") @Valid @RequestBody floorRequest: FloorRequest) : ResponseEntity<Floor> {
        log.info("Creating floor $floorRequest")

        if (!floorService.createFloor(floorRequest)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("floors")

        return ResponseEntity.ok(Floor(floorRequest.floorNumber))
    }

    @Operation(summary = "Get all floors")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Floors retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveFloors() : ResponseEntity<List<Floor>> {
        log.info("Retrieving floors")

        val floors = floorService.getAllFloors() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(floors)
    }

    @Operation(summary = "Get a floor by number")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Floor retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{floorNumber}", produces= ["application/json"])
    fun retrieveFloor(@ApiParam(value = "Floor number", required = true) @Valid @PathVariable floorNumber: Int) : ResponseEntity<Floor> {
        log.info("Retrieving floor $floorNumber")

        val floor = floorService.getFloorByNumber(floorNumber) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(floor)
    }

    @Operation(summary = "Update a floor")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Floor updated"),
        ApiResponse(responseCode = "400", description = "Invalid floor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{floorNumber}", produces= ["application/json"])
    fun updateFloor(@ApiParam(value = "Floor number", required = true) @Valid @PathVariable floorNumber: Int,
                    @SwaggerRequestBody(description = "Request to update a floor") @Valid @RequestBody updateFloorRequest: UpdateFloorRequest) : ResponseEntity<Floor> {
        log.info("Updating floor $updateFloorRequest")

        if (floorService.getFloorByNumber(floorNumber) == null) {
            return ResponseEntity.notFound().build()
        }
        updateFloorRequest.newFloorNumber?.let {
            if (!floorService.updateFloor(floorNumber, it)) {
                return ResponseEntity.badRequest().build()
            }
        } ?: return ResponseEntity.noContent().build()
        replConfig.regenerateSingleModel().invoke("floors")

        return ResponseEntity.ok(Floor(updateFloorRequest.newFloorNumber))
    }

    @Operation(summary = "Delete a floor")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Floor deleted"),
        ApiResponse(responseCode = "400", description = "Invalid floor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{floorNumber}", produces= ["application/json"])
    fun deleteFloor(@ApiParam(value = "Floor number", required = true) @Valid @PathVariable floorNumber: Int) : ResponseEntity<String> {
        log.info("Deleting floor $floorNumber")

        if (floorService.getFloorByNumber(floorNumber) == null) {
            return ResponseEntity.notFound().build()
        }
        if (!floorService.deleteFloor(floorNumber)) {
            return ResponseEntity.notFound().build()
        }
        replConfig.regenerateSingleModel().invoke("floors")

        return ResponseEntity.ok("Floor deleted")
    }
}