package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_patient_trajectory")
class PatientTrajectory (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "id")
    var patientId : Patient,

    @Column(name = "trajectory_date")
    var date: LocalDate,

    @Column(name = "daily_need")
    var need: String
) {
    fun setDate(dayOffset: Int) : LocalDate {
        return LocalDate.now().plusDays(dayOffset.toLong())
    }
}