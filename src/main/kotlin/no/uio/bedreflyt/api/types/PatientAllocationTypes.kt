package no.uio.bedreflyt.api.types

data class PatientAllocationRequest (
    val patientId: String,
    val acute: Boolean,
    val diagnosisCode: String,
    val diagnosisName: String,
    val acuteCategory: Int?,
    val careCategory: Int?,
    val monitoringCategory: Int?,
    val careId: Int?,
    val contagious: Boolean,
    val roomNumber: Int?
)

data class UpdatePatientAllocationRequest (
    val newAcute: Boolean?,
    val newDiagnosisCode: String?,
    val newDiagnosisName: String?,
    val newAcuteCategory: Int?,
    val newCareCategory: Int?,
    val newMonitoringCategory: Int?,
    val newCareId: Int?,
    val newContagious: Boolean?,
    val newRoomNumber: Int?
)
