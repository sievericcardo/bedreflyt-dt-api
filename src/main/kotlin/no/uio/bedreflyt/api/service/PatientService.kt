package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.repository.PatientRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PatientService @Autowired constructor(
    private val patientRepository: PatientRepository
) {
    fun getPatient(patientId: String): Patient {
        return patientRepository.findByPatientId(patientId) ?: throw IllegalArgumentException("Patient not found")
    }
}