package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class OfficeRequest (
    @NotBlank(message = "Room number is required")
    @NotNull(message = "Room number is required")
    @Pattern(regexp = "^[0-9]+$", message = "Room number is invalid")
    val roomNumber: Int,
    @NotBlank(message = "Room capacity is required")
    @NotNull(message = "Room capacity is required")
    @Pattern(regexp = "^[0-9]+$", message = "Room capacity is invalid")
    val capacity: Int,
    @NotNull(message = "Available status is required")
    @NotBlank(message = "Available status is required")
    @Pattern(regexp = "^(true|false)$", message = "Available status must be true or false")
    val available: Boolean,
    @NotBlank(message = "Ward is required")
    @NotNull(message = "Ward is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Ward is invalid")
    val ward: String,
    @NotBlank(message = "Hospital is required")
    @NotNull(message = "Hospital is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Hospital is invalid")
    val hospital: String,
    @NotBlank(message = "Monitoring category is required")
    @NotNull(message = "Monitoring category is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Monitoring category description is invalid")
    val categoryDescription: String
)

data class UpdateOfficeRequest (
    @Nullable
    @Pattern(regexp = "^[0-9]+$", message = "Room number is invalid")
    val newCapacity: Int?,
    @Nullable
    @Pattern(regexp = "^(true|false)$", message = "Available status must be true or false")
    val newAvailable: Boolean?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Ward is invalid")
    val newWard: String?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Monitoring category description is invalid")
    val newCategoryDescription: String?
)