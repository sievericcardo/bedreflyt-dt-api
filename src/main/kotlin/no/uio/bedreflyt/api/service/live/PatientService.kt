package no.uio.bedreflyt.api.service.live

//import jakarta.persistence.OptimisticLockException
import jakarta.transaction.Transactional
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.repository.live.PatientRepository
import org.springframework.stereotype.Service


@Service
class PatientService (
    private val patientRepository: PatientRepository
) {
    fun findAll(): MutableList<Patient?> {
        return patientRepository.findAll()
    }

    fun findTop20Patients(): List<Patient> {
        return patientRepository.findTop20ByOrderByPatientIdDesc()
    }

    fun findByPatientId(patientId: String): List<Patient> {
        return patientRepository.findByPatientId(patientId)
    }

    fun savePatient(patient: Patient): Patient {
        return patientRepository.save(patient)
    }

    @Transactional
    fun updatePatient(patient: Patient): Patient {
        return patientRepository.save(patient)
    }

//    fun updatePatient(patient: Patient, counterValue: Int = 0) {
//        while (counterValue <= 10) {
//            try {
//                patientRepository.save(patient)
//                return
//            } catch (e: OptimisticLockException) {
//                println("OptimisticLockException caught, retrying")
//                updatePatient(patient, counterValue + 1)
//            }
//        }
//        throw RuntimeException("Failed to update counter after retries")
//    }

    fun deletePatient(patient: Patient) {
        patientRepository.delete(patient)
    }
}