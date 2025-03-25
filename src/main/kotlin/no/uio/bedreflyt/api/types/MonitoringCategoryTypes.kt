package no.uio.bedreflyt.api.types

data class MonitoringCategoryRequest (
    val category: Int,
    val description: String
)

data class UpdateMonitoringCategoryRequest (
    val newDescription: String?
)
