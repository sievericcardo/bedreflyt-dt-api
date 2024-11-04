package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.Patient
import no.uio.bedreflyt.api.model.simulation.ScenarioSim
import org.springframework.data.jpa.repository.JpaRepository

interface ScenarioSimRepository : JpaRepository<ScenarioSim?, Long?> {
    fun findByBatch(batch: Int): ScenarioSim
    fun findByPatientId(patientId: Patient): ScenarioSim
    fun findByTreatmentName(treatmentName: String): ScenarioSim
}