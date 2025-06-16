package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class RoomRequest (
    @NotBlank(message = "Room number is required")
    @NotNull(message = "Room number is required")
    @Pattern(regexp = "^[0-9]+$", message = "Room number is invalid")
    val roomNumber: Int,
    @NotBlank(message = "Room capacity is required")
    @NotNull(message = "Room capacity is required")
    @Pattern(regexp = "^[0-9]+$", message = "Room capacity is invalid")
    val capacity: Int,
    @NotNull(message = "Penalty is required")
    @NotBlank(message = "Penalty is required")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Penalty must be a valid number with up to two decimal places")
    val penalty: Double?,
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

data class UpdateRoomRequest (
    @Nullable
    @Pattern(regexp = "^[0-9]+$", message = "Room number is invalid")
    val newCapacity: Int?,
    @NotNull(message = "Penalty is required")
    @NotBlank(message = "Penalty is required")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Penalty must be a valid number with up to two decimal places")
    val newPenalty: Double?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Ward is invalid")
    val newWard: String?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "Monitoring category description is invalid")
    val newCategoryDescription: String?
)

