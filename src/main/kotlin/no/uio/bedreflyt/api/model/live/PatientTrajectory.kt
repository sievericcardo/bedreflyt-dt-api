package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDate
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
    var date: LocalDate,

    @Column(name = "daily_need")
    var need: Int
) {
    fun setDate(dayOffset: Int) : LocalDate {
        return LocalDate.now().plusDays(dayOffset.toLong())
    }

    fun getBatchDay() : Int {
        return ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    }
}