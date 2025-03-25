package no.uio.bedreflyt.api.types

data class RoomRequest (
    val roomNumber: Int,
    val capacity: Int,
    val ward: String,
    val hospital: String,
    val categoryDescription: String
)

data class UpdateRoomRequest (
    val newCapacity: Int?,
    val newWard: String?,
    val newCategoryDescription: String?
)

