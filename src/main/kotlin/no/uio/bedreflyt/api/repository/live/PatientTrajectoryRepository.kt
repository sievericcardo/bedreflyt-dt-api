package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import org.springframework.data.jpa.repository.JpaRepository

interface PatientTrajectoryRepository : JpaRepository<PatientTrajectory, Long> {
    fun findByPatientId(patientId: Patient): List<PatientTrajectory>?
}