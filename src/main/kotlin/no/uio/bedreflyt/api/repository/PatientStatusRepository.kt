package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.model.PatientStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PatientStatusRepository : JpaRepository<PatientStatus?, Long?> {
    fun findByPatientId(patientId: Patient): PatientStatus
}