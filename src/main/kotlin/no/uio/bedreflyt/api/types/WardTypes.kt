package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WardRequest (
    @NotBlank(message = "Ward name is required")
    @NotNull(message = "Ward name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Ward name is invalid")
    val wardName: String,
    @NotBlank(message = "Ward hospital name is required")
    @NotNull(message = "Ward hospital name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Ward hospital name is invalid")
    val wardCode: String?,
    @NotBlank(message = "Capacity threshold is required")
    @NotNull(message = "Capacity threshold is required")
    @Pattern(regexp = "^-?\\d+(\\.\\d+)?$", message = "Capacity Threshold is invalid")
    val capacityThreshold: Double,
    @NotBlank(message = "Corridor penalty is required")
    @NotNull(message = "Corridor penalty is required")
    @Pattern(regexp = "^-?\\d+(\\.\\d+)?$", message = "Corridor penalty is invalid")
    val corridorPenalty: Double,
    @NotBlank(message = "Office penalty is required")
    @NotNull(message = "Office penalty is required")
    @Pattern(regexp = "^-?\\d+(\\.\\d+)?$", message = "Office penalty is invalid")
    val officePenalty: Double,
    @NotBlank(message = "Corridor capacity is required")
    @NotNull(message = "Corridor capacity is required")
    @Pattern(regexp = "^[0-9]+$", message = "Corridor capacity is invalid")
    val corridorCapacity: Int,
    @NotBlank(message = "Hospital name is required")
    @NotNull(message = "Hospital name is required")
    val wardHospitalName: String,
    @NotBlank(message = "Ward floor number is required")
    @NotNull(message = "Ward floor number is required")
    @Pattern(regexp = "^[0-9]+$", message = "Ward floor number is invalid")
    val wardFloorNumber: Int
)

data class UpdateWardRequest (
    @Nullable
    @Pattern(regexp = "^[0-9]+$", message = "Ward floor number is invalid")
    val newWardFloorNumber: Int?,
)
