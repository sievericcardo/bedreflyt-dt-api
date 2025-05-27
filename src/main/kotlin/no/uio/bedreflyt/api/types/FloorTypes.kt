package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class FloorRequest (
    @NotBlank(message = "Floor number is required")
    @NotNull(message = "Floor number is required")
    @Pattern(regexp = "^[0-9]+$", message = "Floor number is invalid")
    val floorNumber: Int
)

data class UpdateFloorRequest (
    @Nullable
    @Pattern(regexp = "^[0-9]+$", message = "Floor number is invalid")
    val newFloorNumber: Int?
)
