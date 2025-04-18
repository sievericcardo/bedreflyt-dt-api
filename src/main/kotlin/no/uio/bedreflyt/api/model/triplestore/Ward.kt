package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Ward (
    val wardName: String,
    val wardCode: String?,
    val wardHospital: Hospital,
    val wardFloor: Floor
) : Serializable
{
    override fun toString(): String {
        return "Ward(wardName='$wardName', wardCode=$wardCode, wardHospital=$wardHospital, wardFloor=$wardFloor)"
    }
}