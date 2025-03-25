package no.uio.bedreflyt.api.types

data class WardRequest (
    val wardName: String,
    val wardCode: String?,
    val wardHospitalName: String,
    val wardFloorNumber: Int
)

data class UpdateWardRequest (
    val newWardFloorNumber: Int?,
)
