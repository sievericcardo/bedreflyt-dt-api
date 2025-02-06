package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientAllocation
import org.springframework.data.jpa.repository.JpaRepository

interface PatientAllocationRepository  : JpaRepository<PatientAllocation, Long> {
    fun findByPatientId(patientId: Patient): PatientAllocation
}