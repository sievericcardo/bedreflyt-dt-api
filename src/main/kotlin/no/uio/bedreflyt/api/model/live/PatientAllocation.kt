package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "patient_allocation")
class PatientAllocation (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "id")
    var patientId : Patient,

    @Column(name = "acute")
    var acute : Boolean = false,

    @Column(name = "main_diagnosis_code")
    var diagnosisCode : String = "",

    @Column(name = "main_diagnosis_name")
    var diagnosisName : String = "",

    @Column(name = "acute_category")
    var acuteCategory : Int = 0,

    @Column(name = "care_category")
    var careCategory : Int = 0,

    @Column(name = "monitoring_category")
    var monitoringCategory : Int = 0,

    @Column(name = "care_id")
    var careId : Int = 0,

    @Column(name = "contagious")
    var contagious : Boolean = false,

    @Column(name = "roomNumber")
    var roomNumber : Int = -1,
)