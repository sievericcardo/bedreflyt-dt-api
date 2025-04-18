package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Treatment (
    val treatmentName: String,
    val treatmentDescription: String?,
    val diagnosis: Diagnosis,
    val frequency: Double,
    val weight: Double,
    val firstTaskName: String,
    val lastTaskName: String
) : Serializable
{
    override fun toString(): String {
        return "Treatment(treatmentName='$treatmentName', treatmentDescription=$treatmentDescription, diagnosis=$diagnosis, frequency=$frequency, weight=$weight, firstTaskName='$firstTaskName', lastTaskName='$lastTaskName')"
    }
}