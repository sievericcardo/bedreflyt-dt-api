package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.PatientStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PatientStatusRepository : JpaRepository<PatientStatus?, String?> {
    fun findByPatientId(patientId: String?): PatientStatus? {
        return null
    }
}