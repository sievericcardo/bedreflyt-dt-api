package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.model.simulation.PatientStatusSim
import no.uio.bedreflyt.api.repository.simulation.PatientStatusSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PatientStatusSimService @Autowired constructor(
    private val patientStatusSimRepository: PatientStatusSimRepository
) {
    fun findAll() : MutableList<PatientStatusSim?> {
        return patientStatusSimRepository.findAll()
    }

    fun findByPatientId(patientId: PatientSim, sqliteDbUrl: String? = null): PatientStatusSim {
        return patientStatusSimRepository.findByPatientId(patientId)
    }

    fun savePatientStatus(patientStatus: PatientStatusSim, sqliteDbUrl: String? = null): PatientStatusSim {
        return patientStatusSimRepository.save(patientStatus)
    }
}