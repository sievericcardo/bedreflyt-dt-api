package no.uio.bedreflyt.api.service.live

import jakarta.transaction.Transactional
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import no.uio.bedreflyt.api.repository.live.PatientAllocationRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
open class PatientAllocationService (
    private val patientAllocationRepository: PatientAllocationRepository
) {
    open fun findAll(): MutableList<PatientAllocation>? {
        return patientAllocationRepository.findAll()
    }

    open fun findByPatientId(patientId: Patient): PatientAllocation? {
        return patientAllocationRepository.findByPatientId(patientId)
    }

    open fun findByWardNameAndHospitalCode(wardName: String, hospitalCode: String): List<PatientAllocation>? {
        return patientAllocationRepository.findByWardNameAndHospitalCode(wardName, hospitalCode)
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

    open fun deleteExpiredAllocation() {
        val allocations = findAll() ?: emptyList()
        // for each check if the date is expired
        allocations.forEach { allocation ->
            if (allocation.dueDate.isBefore(LocalDateTime.now())) {
                patientAllocationRepository.delete(allocation)
            }
        }
    }

    open fun deletePatientAllocationWithOffset(offset: Long) {
        val allocations = findAll() ?: emptyList()
        // for each check if the date is expired
        allocations.forEach { allocation ->
            if (allocation.dueDate.isBefore(LocalDateTime.now().plusDays(offset))) {
                patientAllocationRepository.delete(allocation)
            }
        }
    }
}