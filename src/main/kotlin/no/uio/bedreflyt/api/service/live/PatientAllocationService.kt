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

    open fun findAllSimulated(): List<PatientAllocation>? {
        return patientAllocationRepository.findBySimulatedTrue()
    }

    open fun findByPatientId(patientId: Patient, simulated : Boolean = false): PatientAllocation? {
        val allocations = patientAllocationRepository.findByPatientId(patientId)

        if (allocations == null) {
            return null
        }
        return allocations.firstOrNull { it.simulated == simulated }
    }

    open fun findByWardNameAndHospitalCode(wardName: String, hospitalCode: String): List<PatientAllocation>? {
        return patientAllocationRepository.findByWardNameAndHospitalCode(wardName, hospitalCode)
    }

    open fun findByWardNameAndHospitalCodeSimulated(wardName: String, hospitalCode: String): List<PatientAllocation>? {
        return patientAllocationRepository.findByWardNameAndHospitalCode(wardName, hospitalCode)?.filter { it.simulated }
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
        val allocations = findAllSimulated() ?: emptyList()
        // for each check if the date is expired
        allocations.forEach { allocation ->
            if (allocation.dueDate.isBefore(LocalDateTime.now().plusDays(offset))) {
                patientAllocationRepository.delete(allocation)
            }
        }
    }
}