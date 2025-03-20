package no.uio.bedreflyt.api.types

data class CityRequest (
    val cityName: String
)

typealias UpdateCityRequest = CityRequest
typealias DeleteCityRequest = CityRequest
