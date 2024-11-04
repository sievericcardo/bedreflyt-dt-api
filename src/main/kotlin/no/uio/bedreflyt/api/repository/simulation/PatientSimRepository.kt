package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.Patient
import org.springframework.data.jpa.repository.JpaRepository

interface PatientSimRepository : JpaRepository<Patient?, String?> {
    fun findByPatientId(patientId: String): Patient
}