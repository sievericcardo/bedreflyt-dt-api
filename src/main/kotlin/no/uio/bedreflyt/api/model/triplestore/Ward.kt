package no.uio.bedreflyt.api.model.triplestore

class Ward (
    val wardName: String,
    val wardCode: String?,
    val wardHospital: Hospital,
    val wardFloor: Floor
)