package no.uio.bedreflyt.api.model.triplestore

class JourneyStep (
    val bedType: Long,
    val diagnosis: String,
    val orderInJourney: Int,
    val task: String
)