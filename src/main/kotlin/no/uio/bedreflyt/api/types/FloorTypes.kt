package no.uio.bedreflyt.api.types

data class FloorRequest (
    val floorNumber: Int
)

typealias UpdateFloorRequest = FloorRequest
typealias DeleteFloorRequest = FloorRequest