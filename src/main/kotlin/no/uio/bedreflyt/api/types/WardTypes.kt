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
