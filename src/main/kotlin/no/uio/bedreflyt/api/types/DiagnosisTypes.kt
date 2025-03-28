package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class DiagnosisRequest (
    @NotBlank(message = "Diagnosis name is required")
    @NotNull(message = "Diagnosis name is required")
    // The Pattern for ICD-10 codes is letter two digit, possible dot and eventually more digits
    @Pattern(regexp = "^[A-Z][0-9]{2}(\\.[0-9]+)?$", message = "Diagnosis name is invalid")
    val diagnosisName : String,
)

data class UpdateDiagnosisRequest (
    @Nullable
    @Pattern(regexp = "^[A-Z][0-9]{2}(\\.[0-9]+)?$", message = "Diagnosis name is invalid")
    val newDiagnosisName : String?,
)