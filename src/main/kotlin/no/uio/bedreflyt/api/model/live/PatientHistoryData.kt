package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "patient_history")
class PatientHistoryData (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "id")
    var patientId : Patient,

    @Column(name = "operation_id")
    var operationId : String = "",

    @Column(name = "operation_start")
    var operationStart : LocalDateTime? = null,

    @Column(name = "operation_end")
    var operationEnd : LocalDateTime? = null,

    @Column(name = "operation_length_days")
    var operationLengthDays : Float = 0.0f,

    @Column(name = "postop_bedhours_cat")
    var postOperationBedtimeHoursCategory : Int = 0,

    @Column(name = "lengh_stay_cat")
    var lengthStayDaysCategory : Int = 0,
)