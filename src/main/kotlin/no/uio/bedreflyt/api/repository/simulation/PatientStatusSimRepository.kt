package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.model.simulation.PatientStatusSim
import org.springframework.data.jpa.repository.JpaRepository

interface PatientStatusSimRepository : JpaRepository<PatientStatusSim?, Long?> {
    fun findByPatientId(patientId: PatientSim): PatientStatusSim
}