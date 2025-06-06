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
import no.uio.bedreflyt.api.model.triplestore.City
import no.uio.bedreflyt.api.service.triplestore.CityService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import no.uio.bedreflyt.api.types.CityRequest
import no.uio.bedreflyt.api.types.UpdateCityRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/cities")
class CityController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val cityService: CityService
) {

    private val log: Logger = LoggerFactory.getLogger(CityController::class.java)

    @Operation(summary = "Add a new city")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City added"),
        ApiResponse(responseCode = "400", description = "Invalid city"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createCity(@SwaggerRequestBody(description = "Request to add a new city") @Valid @RequestBody cityRequest: CityRequest) : ResponseEntity<City> {
        log.info("Creating city $cityRequest")

        val newCity = cityService.createCity(cityRequest) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(newCity)
    }

    @Operation(summary = "Get all cities")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cities retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveCities() : ResponseEntity<List<City>> {
        log.info("Retrieving all cities")

        val cities = cityService.getAllCities() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(cities)
    }

    @Operation(summary = "Get a city by name")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{cityName}", produces= ["application/json"])
    fun retrieveCityByName(@ApiParam(value = "City name", required = true) @Valid @PathVariable cityName: String) : ResponseEntity<City> {
        log.info("Retrieving city $cityName")

        val city = cityService.getCityByName(cityName) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(city)
    }

    @Operation(summary = "Update a city")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City updated"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{cityName}", produces= ["application/json"])
    fun updateCity(@ApiParam(value = "City name", required = true) @PathVariable cityName: String,
                   @SwaggerRequestBody(description = "Request to update a city") @Valid  @RequestBody cityRequest: UpdateCityRequest) : ResponseEntity<City> {
        log.info("Updating city $cityRequest")

        if(cityService.getCityByName(cityName) == null) {
            return ResponseEntity.notFound().build()
        }
        val updatedCity = cityRequest.newCityName?.let {
            log.info("New city name: $it")
            cityService.updateCity(cityName, it) ?: return ResponseEntity.badRequest().build()
        } ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(updatedCity)
    }

    @Operation(summary = "Delete a city")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City deleted"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{cityName}", produces= ["application/json"])
    fun deleteCity(@ApiParam(value = "City name", required = true) @Valid @PathVariable cityName: String) : ResponseEntity<String> {
        log.info("Deleting city $cityName")

        if(cityService.getCityByName(cityName) == null) {
            return ResponseEntity.notFound().build()
        }
        if (!cityService.deleteCity(cityName)) {
            return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok("City removed successfully")
    }
}