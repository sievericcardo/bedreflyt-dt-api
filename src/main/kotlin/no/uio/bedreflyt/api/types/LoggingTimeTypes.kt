package no.uio.bedreflyt.api.types

data class TimeLogging (
    val dataRetrievalTime: Long,
    val minSatProblemTime: Long,
    val extraRoomTime: Long
)

data class SolverTimeLogging (
    val prepareDataForSolve: Long,
    val solverTime: Long,
    val postProcessTime: Long
)

data class CompleteTimeLogging (
    val lifecycleManagerTime: TimeLogging,
    val componentsRetrievalTime: Long,
    val absTime: Long,
    val solverTime: SolverTimeLogging
)
