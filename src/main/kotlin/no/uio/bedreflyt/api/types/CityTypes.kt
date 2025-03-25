package no.uio.bedreflyt.api.types

data class CityRequest (
    val cityName: String
)

data class UpdateCityRequest (
    val newCityName: String?
)
