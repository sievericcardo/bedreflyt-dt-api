package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class MonitoringCategoryRequest (
    @NotBlank(message = "Monitoring category value is required")
    @NotNull(message = "Monitoring category value is required")
    @Pattern(regexp = "^[0-9]+$", message = "Monitoring category value is invalid")
    val category: Int,
    @NotBlank(message = "Monitoring category description is required")
    @NotNull(message = "Monitoring category description is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Monitoring category description is invalid")
    val description: String
)

data class UpdateMonitoringCategoryRequest (
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Monitoring category description is invalid")
    val newDescription: String?
)
