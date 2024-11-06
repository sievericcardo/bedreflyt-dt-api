package no.uio.bedreflyt.api.model.simulation

class RoomDistribution (
    val roomNumber : Int,
    val roomNumberModel : Int,
    val room: String,
    val capacity: Int,
    val bathroom: Boolean
) {
}