package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.repository.simulation.PatientSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PatientSimService @Autowired constructor(
    private val patientSimRepository: PatientSimRepository
) {
    fun findAll(): MutableList<PatientSim?> {
        return patientSimRepository.findAll()
    }

    fun findByPatientId(patientId: String): PatientSim {
        return patientSimRepository.findByPatientId(patientId)
    }

    fun savePatientSim(patientSim: PatientSim): PatientSim {
        return patientSimRepository.save(patientSim)
    }
}