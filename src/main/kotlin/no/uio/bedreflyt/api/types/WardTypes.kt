package no.uio.bedreflyt.api.types

data class WardRequest (
    val wardName: String,
    val wardCode: String?,
    val wardHospitalName: String,
    val wardFloorNumber: Int
)

data class UpdateWardRequest (
    val wardName: String,
    val hospitalName: String,
    val newWardFloorNumber: Int,
)

data class DeleteWardRequest (
    val wardName: String,
    val hospitalName: String
)
