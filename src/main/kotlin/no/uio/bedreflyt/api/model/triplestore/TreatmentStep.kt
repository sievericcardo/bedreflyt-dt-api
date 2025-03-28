package no.uio.bedreflyt.api.model.triplestore

class TreatmentStep (
    val treatmentName: String,
    val task: Task,
    val previousTask: String?,
    val nextTask: String?,
    val monitoringCategory: MonitoringCategory,
    val staffLoad: Double,
    val averageDuration: Double
)