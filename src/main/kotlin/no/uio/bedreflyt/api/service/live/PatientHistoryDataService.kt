package no.uio.bedreflyt.api.service.live

import jakarta.transaction.Transactional
import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientHistoryData
import no.uio.bedreflyt.api.repository.live.PatientHistoryDataRepository
import org.springframework.stereotype.Service

@Service
open class PatientHistoryDataService (
    private val patientHistoryDataRepository: PatientHistoryDataRepository
) {

    open fun findAll(): MutableList<PatientHistoryData?> {
        return patientHistoryDataRepository.findAll()
    }

    open fun findByPatientId(patientId: Patient): List<PatientHistoryData> {
        return patientHistoryDataRepository.findByPatientId(patientId)
    }

    open fun savePatientHistoryData(patientHistoryData: PatientHistoryData): PatientHistoryData {
        return patientHistoryDataRepository.save(patientHistoryData)
    }

    @Transactional
    open fun updatePatientHistoryData(patientHistoryData: PatientHistoryData): PatientHistoryData {
        return patientHistoryDataRepository.save(patientHistoryData)
    }

    open fun deletePatientHistoryData(patientHistoryData: PatientHistoryData) {
        patientHistoryDataRepository.delete(patientHistoryData)
    }
}