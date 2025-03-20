package no.uio.bedreflyt.api.types

data class PatientRequest (
    val patientName: String,
    val patientSurname: String,
    val patientAddress: String,
    val city: String,
    val patientBirthdate: String,
    val gender: String
)

data class UpdatePatientRequest (
    val patientId: String,
    val patientName: String?,
    val patientSurname: String?,
    val patientAddress: String?,
    val city: String?,
    val patientBirthdate: String?,
    val gender: String?
)

data class DeletePatientRequest (
    val patientId: String
)