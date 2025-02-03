package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Patient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PatientRepository : JpaRepository<Patient?, String?> {
    fun findByPatientId(patientId: String):  Patient?
}