package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class City (
    val cityName: String
) : Serializable {
    override fun toString(): String {
        return "City(cityName='$cityName')"
    }
}