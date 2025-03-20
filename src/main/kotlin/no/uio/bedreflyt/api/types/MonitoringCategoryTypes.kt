package no.uio.bedreflyt.api.types

data class MonitoringCategoryRequest (
    val category: Int,
    val description: String
)

data class UpdateMonitoringCategoryRequest (
    val category: Int,
    val description: String,
    val newCategory: Int?,
    val newDescription: String?
)

data class DeleteMonitoringCategoryRequest (
    val category: Int
)
