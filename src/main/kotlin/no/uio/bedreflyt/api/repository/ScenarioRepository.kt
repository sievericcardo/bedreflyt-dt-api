package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.model.Scenario
import org.springframework.data.jpa.repository.JpaRepository

interface ScenarioRepository : JpaRepository<Scenario?, Long?> {
    fun findByBatch(batch: Int): Scenario
    fun findByPatientId(patientId: Patient): Scenario
    fun findByTreatmentName(treatmentName: String): Scenario
}