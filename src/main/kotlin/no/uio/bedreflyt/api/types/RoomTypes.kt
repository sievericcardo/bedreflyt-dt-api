package no.uio.bedreflyt.api.types

data class RoomRequest (
    val roomNumber: Int,
    val capacity: Int,
    val ward: String,
    val hospital: String,
    val category: Int
)

data class UpdateRoomRequest (
    val roomNumber: Int,
    val newCapacity: Int?,
    val newWard: String?,
    val newCategory: Int?
)

data class DeleteRoomRequest (
    val roomNumber: Int
)
