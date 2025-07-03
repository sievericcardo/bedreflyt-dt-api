package no.uio.bedreflyt.api.types

data class TimeLogging (
    val dataRetrievalTime: Long,
    val minSatProblemTime: Long,
    val extraRoomTime: Long
)

data class completeTimeLogging (
    val lifecycleManagerTime: TimeLogging,
    val componentsRetrievalTime: Long,
    val absTime: Long,
    val solverTime: Long
)
