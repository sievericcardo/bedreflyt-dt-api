package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.PatientSim
import org.springframework.data.jpa.repository.JpaRepository

interface PatientSimRepository : JpaRepository<PatientSim?, String?> {
    fun findByPatientId(patientId: String): PatientSim
}