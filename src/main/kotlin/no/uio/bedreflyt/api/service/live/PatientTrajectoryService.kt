package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import no.uio.bedreflyt.api.repository.live.PatientTrajectoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
open class PatientTrajectoryService (
    private val patientTrajectoryRepository: PatientTrajectoryRepository
) {
    open fun findAll(): List<PatientTrajectory>? {
        return patientTrajectoryRepository.findAll()
    }

    open fun findAllSimulated() : List<PatientTrajectory>? {
        return patientTrajectoryRepository.findAll().filter { it.simulated }
    }

    open fun findByPatientId(patientId: Patient, simulated: Boolean = false): List<PatientTrajectory>? {
        return patientTrajectoryRepository.findByPatientId(patientId)?.filter { it.simulated == simulated }
    }

    open fun savePatientTrajectory(patientTrajectory: PatientTrajectory): PatientTrajectory {
        return patientTrajectoryRepository.save(patientTrajectory)
    }

    open fun updatePatientTrajectory(patientTrajectory: PatientTrajectory): PatientTrajectory {
        return patientTrajectoryRepository.save(patientTrajectory)
    }

    open fun deletePatientTrajectory(patientTrajectory: PatientTrajectory) {
        patientTrajectoryRepository.delete(patientTrajectory)
    }

    open fun deleteExpiredTrajectory() {
        val trajectories = findAll() ?: emptyList()
        // for each check if the date is expired
        trajectories.forEach { trajectory ->
            if (trajectory.date.isBefore(LocalDateTime.now())) {
                patientTrajectoryRepository.delete(trajectory)
            }
        }
    }

    open fun deleteExpiredTrajectoryWithOffset(offset: Long) {
        val trajectories = findAllSimulated() ?: emptyList()
        // for each check if the date is expired
        trajectories.forEach { trajectory ->
            if (trajectory.date.isBefore(LocalDateTime.now().plusDays(offset))) {
                patientTrajectoryRepository.delete(trajectory)
            }
        }
    }
}