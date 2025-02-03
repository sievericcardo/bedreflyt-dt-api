package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.PatientHistoryData
import org.springframework.data.jpa.repository.JpaRepository

interface PatientHistoryDataRepository : JpaRepository<PatientHistoryData?, Long?> {
    fun findByPatientId(patientId: String): List<PatientHistoryData>
}