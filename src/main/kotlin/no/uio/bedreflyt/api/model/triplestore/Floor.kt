package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Floor (
    val floorNumber: Int
) : Serializable
{
    override fun toString(): String {
        return "Floor(floorNumber=$floorNumber)"
    }
}