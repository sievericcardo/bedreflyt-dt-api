package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class TaskRequest (
    @NotBlank(message = "Task name is required")
    @NotNull(message = "Task name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Task name is invalid")
    val taskName : String
)

data class UpdateTaskRequest (
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Task name is invalid")
    val newTaskName: String?
)

