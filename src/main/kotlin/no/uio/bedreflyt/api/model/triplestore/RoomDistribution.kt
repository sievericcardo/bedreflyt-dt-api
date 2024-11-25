package no.uio.bedreflyt.api.model.triplestore

class RoomDistribution (
    val roomNumber : Int,
    val roomNumberModel : Int,
    val room: Long,
    val capacity: Int,
    val bathroom: Boolean
)