package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.model.PatientStatus
import no.uio.bedreflyt.api.repository.PatientStatusRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PatientStatusService @Autowired constructor(
    private val patientStatusRepository: PatientStatusRepository
) {
    fun findByPatientId(patientId: Patient): PatientStatus {
        return patientStatusRepository.findByPatientId(patientId)
    }
}