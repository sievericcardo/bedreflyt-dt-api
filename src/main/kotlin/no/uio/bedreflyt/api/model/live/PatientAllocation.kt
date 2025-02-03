package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime

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
    var mainDiagnosisCode : String = "",

    @Column(name = "main_diagnosis_name")
    var mainDiagnosisName : String = "",

    @Column(name = "acute_category")
    var acuteCategory : Int = 0,

    @Column(name = "care_category")
    var careCategory : Int = 0,

    @Column(name = "monitoring_category")
    var monitoringCategory : Int = 0,

    @Column(name = "care_id")
    var careId : String = "",

    @Column(name = "infectious")
    var infectious : Boolean = false,

    @Column(name = "roomNumber")
    var roomNumber : Int = -1,
)