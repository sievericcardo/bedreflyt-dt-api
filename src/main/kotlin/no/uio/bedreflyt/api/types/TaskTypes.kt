package no.uio.bedreflyt.api.types

data class TaskRequest (
    val taskName : String
)

data class UpdateTaskRequest (
    val newTaskName: String?
)

