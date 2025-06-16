package no.uio.bedreflyt.api.model.triplestore

class Office (
    roomNumber: Int,
    capacity: Int,
    penalty: Double,
    var available: Boolean,
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
) : Room (roomNumber, capacity, penalty)