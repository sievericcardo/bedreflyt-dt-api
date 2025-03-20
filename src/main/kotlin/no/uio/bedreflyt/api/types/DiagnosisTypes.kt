package no.uio.bedreflyt.api.types

data class DiagnosisRequest (
    val diagnosisName : String,
)

data class UpdateDiagnosisRequest (
    val oldDiagnosisName : String,
    val newDiagnosisName : String,
)