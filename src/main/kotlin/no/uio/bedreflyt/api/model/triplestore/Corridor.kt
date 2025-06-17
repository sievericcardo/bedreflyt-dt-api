package no.uio.bedreflyt.api.model.triplestore

class Corridor (
    roomNumber: Int,
    capacity: Int,
    penalty: Double,
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
) : Room (roomNumber, capacity, penalty)