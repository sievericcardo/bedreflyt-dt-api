package no.uio.bedreflyt.api.model.simulation

class PatientStatus (
    val patientId: String,
    val infectious: Boolean,
    val room : String
)