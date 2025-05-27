package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "daily_patient_trajectory")
class PatientTrajectory (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "patient_id")
    var patientId : Patient,

    @Column(name = "trajectory_date")
    var date: LocalDateTime,

    @Column(name = "daily_need")
    var need: Int,

    @Column(name = "simulated")
    var simulated: Boolean = false,
) {
    constructor() : this(
        null,
        Patient(),
        LocalDateTime.now(),
        0
    )

    constructor (
        patientId: Patient,
        date: LocalDateTime,
        need: Int,
        simulated: Boolean = false
    ) : this(
        null,
        patientId,
        date,
        need,
        simulated
    )

    fun setDate(offset: Int) : LocalDateTime {
        return LocalDateTime.now().plusHours(offset.toLong())
    }

    fun getBatchDay() : Int {
        return ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
    }

    fun getBatchHour(): Int {
        return ChronoUnit.HOURS.between(LocalDate.now().atStartOfDay(), date.toLocalDate().atStartOfDay()).toInt()
    }
}