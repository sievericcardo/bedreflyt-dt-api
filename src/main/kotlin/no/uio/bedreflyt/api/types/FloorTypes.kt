package no.uio.bedreflyt.api.types

data class FloorRequest (
    val floorNumber: Int
)

data class UpdateFloorRequest (
    val newFloorNumber: Int?
)
