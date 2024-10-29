package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.Patient
import org.springframework.data.jpa.repository.JpaRepository

interface PatientRepository : JpaRepository<Patient?, String?> {
    fun findByPatientId(patientId: String): Patient
}