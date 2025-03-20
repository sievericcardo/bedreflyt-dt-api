package no.uio.bedreflyt.api.types

import no.uio.bedreflyt.api.model.triplestore.TreatmentStep

data class TreatmentRequest (
    val treatmentName: String,
    val treatmentDescription: String?,
    val diagnosis: String,
    val frequency: Double,
    val weight: Double,
    val firstStep: TreatmentStep,
    val lastStep: TreatmentStep,
    val steps: List<TreatmentStep>
)

data class UpdateTreatmentRequest (
    val treatmentName: String,
    val newTreatmentDescription: String?,
    val newDiagnosis: String?,
    val newFrequency: Double?,
    val newWeight: Double?,
    val newFirstStep: TreatmentStep?,
    val newLastStep: TreatmentStep?,
)

data class DeleteTreatmentRequest (
    val treatmentName: String
)