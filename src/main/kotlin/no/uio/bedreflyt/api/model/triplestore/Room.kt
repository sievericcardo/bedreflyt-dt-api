package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

open class Room (
    val roomNumber : Int,
    val capacity: Int,
    val penalty: Double,
) : Serializable
{
    override fun toString(): String {
        return "Room(roomNumber=$roomNumber, capacity=$capacity)"
    }
}