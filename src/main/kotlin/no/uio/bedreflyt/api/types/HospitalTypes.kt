package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class HospitalRequest (
    @NotBlank(message = "Hospital name is required")
    @NotNull(message = "Hospital name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Hospital name is invalid")
    val hospitalName: String,
    @NotBlank(message = "Hospital name is required")
    @NotNull(message = "Hospital name is required")
    val hospitalCode: String,
    @NotBlank(message = "City name is required")
    @NotNull(message = "City name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "City name is invalid")
    val city: String
)

data class UpdateHospitalRequest (
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Hospital name is invalid")
    val newHospitalName: String?
)
