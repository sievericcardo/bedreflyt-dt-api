package no.uio.bedreflyt.api.types

data class TaskRequest (
    val taskName : String
)

data class UpdateTaskRequest (
    val taskName: String,
    val newTaskName: String
)

data class DeleteTaskRequest (
    val taskName: String
)
