package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class TreatmentStep (
    val treatmentName: String,
    val task: Task,
    val stepNumber: Int,
    val previousTask: String?,
    val nextTask: String?,
    val monitoringCategory: MonitoringCategory,
    val staffLoad: Double,
    val averageDuration: Double
) : Serializable
{
    override fun toString(): String {
        return "TreatmentStep(treatmentName='$treatmentName', task=$task, stepNumber=$stepNumber, previousTask=$previousTask, nextTask=$nextTask, monitoringCategory=$monitoringCategory, staffLoad=$staffLoad, averageDuration=$averageDuration)"
    }
}