package no.uio.bedreflyt.api.model.triplestore

class Treatment (
    val treatmentName: String,
    val treatmentDescription: String?,
    val diagnosis: Diagnosis,
    val frequency: Double,
    val weight: Double,
    val firstTaskName: String,
    val lastTaskName: String
)