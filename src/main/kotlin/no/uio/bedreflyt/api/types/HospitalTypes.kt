package no.uio.bedreflyt.api.types

data class HospitalRequest (
    val hospitalName: String,
    val hospitalCode: String,
    val city: String
)

data class UpdateHospitalRequest (
    val hospitalCode: String,
    val newHospitalName: String
)

data class DeleteHospitalRequest (
    val hospitalCode: String
)
