package no.uio.bedreflyt.api.service.live

import jakarta.transaction.Transactional
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.repository.live.PatientAllocationRepository
import org.springframework.stereotype.Service

@Service
open class PatientAllocationService (
    private val patientAllocationRepository: PatientAllocationRepository
) {
    open fun findAll(): MutableList<PatientAllocation?> {
        return patientAllocationRepository.findAll()
    }

    open fun findByPatientId(patientId: Patient): List<PatientAllocation> {
        return patientAllocationRepository.findByPatientId(patientId)
    }

    open fun savePatientAllocation(patientAllocation: PatientAllocation): PatientAllocation {
        return patientAllocationRepository.save(patientAllocation)
    }

    @Transactional
    open fun updatePatientAllocation(patientAllocation: PatientAllocation): PatientAllocation {
        return patientAllocationRepository.save(patientAllocation)
    }

    open fun deletePatientAllocation(patientAllocation: PatientAllocation) {
        patientAllocationRepository.delete(patientAllocation)
    }
}