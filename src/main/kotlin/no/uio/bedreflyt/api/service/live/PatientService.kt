package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.repository.live.PatientRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PatientService @Autowired constructor(
    private val patientRepository: PatientRepository
) {
    fun findAll(): MutableList<Patient?> {
        return patientRepository.findAll()
    }

    fun findByPatientId(patientId: String): Patient {
        return patientRepository.findByPatientId(patientId)
    }

    fun savePatient(patient: Patient): Patient {
        return patientRepository.save(patient)
    }
}