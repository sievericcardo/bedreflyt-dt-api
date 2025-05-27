package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Diagnosis (
    val diagnosisName: String
) : Serializable
{
    override fun toString(): String {
        return "Diagnosis(diagnosisName='$diagnosisName')"
    }
}