package no.uio.bedreflyt.api.types

import jakarta.annotation.Nullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class PatientRequest (
    @NotBlank(message = "Patient name is required")
    @NotNull(message = "Patient name is required")
    @Pattern(regexp = "^[a-zA-Z',]+(?:[\\s-][a-zA-Z',]+)*$", message = "Patient name is invalid")
    val patientName: String,
    @NotBlank(message = "Patient name is required")
    @NotNull(message = "Patient name is required")
    @Pattern(regexp = "^[a-zA-Z',]+(?:[\\s-][a-zA-Z',]+)*$", message = "Patient surname is invalid")
    val patientSurname: String,
    @NotBlank(message = "Patient address is required")
    @NotNull(message = "Patient address is required")
    @Pattern(regexp = "^[a-zA-Z0-9',]+(?:[\\s-][a-zA-Z0-9',]+)*$", message = "Patient address is invalid")
    val patientAddress: String,
    @NotBlank(message = "City name is required")
    @NotNull(message = "City name is required")
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "City name is invalid")
    val city: String,
    @NotBlank(message = "Patient birthdate is required")
    @NotNull(message = "Patient birthdate is required")
    @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "Patient birthdate is invalid")
    val patientBirthdate: String,
    @NotBlank(message = "Gender is required")
    @NotNull(message = "Gender can't be null")
    @Pattern(regexp = "^(Male|Female)$", message = "Gender need to be Male/Female")
    val gender: String
)

data class UpdatePatientRequest (
    @Nullable
    @Pattern(regexp = "^[a-zA-Z',]+(?:[\\s-][a-zA-Z',]+)*$", message = "Patient name is invalid")
    val patientName: String?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z',]+(?:[\\s-][a-zA-Z',]+)*$", message = "Patient surname is invalid")
    val patientSurname: String?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z0-9',]+(?:[\\s-][a-zA-Z0-9',]+)*$", message = "Patient address is invalid")
    val patientAddress: String?,
    @Nullable
    @Pattern(regexp = "^[a-zA-Z]+(?:[\\s-][a-zA-Z]+)*$", message = "City name is invalid")
    val city: String?,
    @Nullable
    @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "Patient birthdate is invalid")
    val patientBirthdate: String?,
    @Nullable
    @Pattern(regexp = "^(Male|Female)$", message = "Gender need to be Male/Female")
    val gender: String?
)