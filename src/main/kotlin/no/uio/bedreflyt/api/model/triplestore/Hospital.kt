package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Hospital (
    val hospitalName: String,
    val hospitalCode: String,
    val hospitalCity: City
) : Serializable
{
    override fun toString(): String {
        return "Hospital(hospitalName='$hospitalName', hospitalCode='$hospitalCode', hospitalCity=$hospitalCity)"
    }
}