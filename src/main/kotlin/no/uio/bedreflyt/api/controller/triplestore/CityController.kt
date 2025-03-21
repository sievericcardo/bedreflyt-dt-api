package no.uio.bedreflyt.api.controller.triplestore

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.City
import no.uio.bedreflyt.api.service.triplestore.CityService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import no.uio.bedreflyt.api.types.CityRequest
import no.uio.bedreflyt.api.types.DeleteCityRequest
import no.uio.bedreflyt.api.types.UpdateCityRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/fuseki/cities")
class CityController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val cityService: CityService
) {

    private val log: Logger = Logger.getLogger(CityController::class.java.name)

    @Operation(summary = "Add a new city")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City added"),
        ApiResponse(responseCode = "400", description = "Invalid city"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createCity(@SwaggerRequestBody(description = "Request to add a new city") @RequestBody cityRequest: CityRequest) : ResponseEntity<City> {
        log.info("Creating city $cityRequest")

        if (!cityService.createCity(cityRequest)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("city")

        return ResponseEntity.ok(City(cityRequest.cityName))
    }

    @Operation(summary = "Get all cities")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cities retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
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
    @GetMapping("/retrieve/{cityName}")
    fun retrieveCityByName(@SwaggerRequestBody(description = "City name") @RequestBody cityName: String) : ResponseEntity<City> {
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
    @PatchMapping("/update")
    fun updateCity(@SwaggerRequestBody(description = "Request to update a city") @RequestBody cityRequest: UpdateCityRequest) : ResponseEntity<City> {
        log.info("Updating city $cityRequest")

        if (!cityService.updateCity(cityRequest.cityName, cityRequest.newCityName)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("city")

        return ResponseEntity.ok(City(cityRequest.cityName))
    }

    @Operation(summary = "Delete a city")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "City deleted"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteCity(@SwaggerRequestBody(description = "Request to delete a city") @RequestBody cityRequest: DeleteCityRequest) : ResponseEntity<City> {
        log.info("Deleting city $cityRequest")

        if (!cityService.deleteCity(cityRequest.cityName)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("city")

        return ResponseEntity.ok(City(cityRequest.cityName))
    }
}