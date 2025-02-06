package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import no.uio.bedreflyt.api.repository.live.PatientTrajectoryRepository
import org.springframework.stereotype.Service

@Service
open class PatientTrajectoryService (
    private val patientTrajectoryRepository: PatientTrajectoryRepository
) {
    open fun findAll(): List<PatientTrajectory>? {
        return patientTrajectoryRepository.findAll()
    }

    open fun findByPatientId(patientId: Patient): List<PatientTrajectory>? {
        return patientTrajectoryRepository.findByPatientId(patientId)
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
}