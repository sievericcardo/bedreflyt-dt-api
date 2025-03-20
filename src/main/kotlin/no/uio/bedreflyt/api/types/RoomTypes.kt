package no.uio.bedreflyt.api.types

data class RoomRequest (
    val roomNumber: Int,
    val capacity: Int,
    val ward: String,
    val hospital: String,
    val categoryDescription: String
)

data class UpdateRoomRequest (
    val roomNumber: Int,
    val newCapacity: Int?,
    val newWard: String?,
    val newCategoryDescription: String?
)

data class DeleteRoomRequest (
    val roomNumber: Int,
    val hospital: String
)
