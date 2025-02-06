package no.uio.bedreflyt.api.service.live

//import jakarta.persistence.OptimisticLockException
import jakarta.transaction.Transactional
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.repository.live.PatientRepository
import org.springframework.stereotype.Service


@Service
open class PatientService (
    private val patientRepository: PatientRepository
) {
    open fun findAll(): List<Patient>? {
        return patientRepository.findAll()
    }

    open fun findByPatientId(patientId: String): Patient? {
        return patientRepository.findByPatientId(patientId)
    }

    open fun savePatient(patient: Patient): Patient {
        return patientRepository.save(patient)
    }

    @Transactional
    open fun updatePatient(patient: Patient): Patient {
        return patientRepository.save(patient)
    }

    open fun deletePatient(patient: Patient) {
        patientRepository.delete(patient)
    }
}