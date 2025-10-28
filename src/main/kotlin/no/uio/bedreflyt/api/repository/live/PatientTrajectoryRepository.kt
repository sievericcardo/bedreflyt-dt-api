package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface PatientTrajectoryRepository : JpaRepository<PatientTrajectory, Long> {
    fun findByPatientId(patientId: Patient): List<PatientTrajectory>?
    fun findBySimulated(simulated: Boolean): List<PatientTrajectory>
    fun findByDateBefore(date: LocalDateTime): List<PatientTrajectory>
}