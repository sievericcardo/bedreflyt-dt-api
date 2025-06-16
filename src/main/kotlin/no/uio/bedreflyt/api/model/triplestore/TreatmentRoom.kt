package no.uio.bedreflyt.api.model.triplestore

class TreatmentRoom (
    roomNumber: Int,
    capacity: Int,
    penalty: Double = 0.0,
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
) : Room (roomNumber, capacity, penalty) {
    val ward: Ward
        get() = treatmentWard // Custom getter for SpEL compatibility

    override fun toString(): String {
        return "TreatmentRoom(roomNumber=$roomNumber, capacity=$capacity, treatmentWard=$treatmentWard, hospital=$hospital, monitoringCategory=$monitoringCategory)"
    }
}