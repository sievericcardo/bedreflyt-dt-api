package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import no.uio.bedreflyt.api.repository.live.PatientTrajectoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    open fun saveAllPatientTrajectories(patientTrajectories: List<PatientTrajectory>): List<PatientTrajectory> {
        return patientTrajectoryRepository.saveAll(patientTrajectories)
    }

    open fun updatePatientTrajectory(patientTrajectory: PatientTrajectory): PatientTrajectory {
        return patientTrajectoryRepository.save(patientTrajectory)
    }

    open fun deletePatientTrajectory(patientTrajectory: PatientTrajectory) {
        patientTrajectoryRepository.delete(patientTrajectory)
    }

    @Transactional
    open fun deleteAllPatientTrajectories() {
        patientTrajectoryRepository.deleteAll()
    }

    @Transactional
    open fun deleteSimulatedPatientTrajectories() {
        val simulatedTrajectories = patientTrajectoryRepository.findBySimulated(true)
        patientTrajectoryRepository.deleteAll(simulatedTrajectories)
    }

    @Transactional
    open fun deleteExpiredTrajectory() {
        val expiredTrajectories = patientTrajectoryRepository.findByDateBefore(LocalDateTime.now())
        patientTrajectoryRepository.deleteAll(expiredTrajectories)
    }

    @Transactional
    open fun deleteExpiredTrajectoryWithOffset(offset: Long) {
        val cutoffTime = LocalDateTime.now().plusDays(offset)
        val trajectoriesToDelete = patientTrajectoryRepository.findByDateBefore(cutoffTime)
        patientTrajectoryRepository.deleteAll(trajectoriesToDelete)
    }

    @Transactional
    open fun deleteTrajectoryByPatient(patient: Patient) {
        val trajectoriesToDelete = patientTrajectoryRepository.findByPatientId(patient)
        if (trajectoriesToDelete != null) {
            patientTrajectoryRepository.deleteAll(trajectoriesToDelete)
        }
    }
}