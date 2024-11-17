package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "patient")
class Patient (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "patient_id")
    var patientId : String = "",

    @Column(name = "operation_id")
    var operationId : String = "",

    @Column(name = "operation_start")
    var operationStart : LocalDateTime? = null,

    @Column(name = "operation_end")
    var operationEnd : LocalDateTime? = null,

    @Column(name = "operation_length_days")
    var operationLengthDays : Float = 0.0f,

    @Column(name = "acute")
    var acute : Boolean = false,

    @Column(name = "gender")
    var gender : String = "",

    @Column(name = "age")
    var age : Int = 0,

    @Column(name = "Oslo")
    var oslo : Boolean = false,

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

    @Column(name = "post_operation_bedtime_hours_category")
    var postOperationBedtimeHoursCategory : Int = 0,

    @Column(name = "lengh_stay_days_category")
    var lengthStayDaysCategory : Int = 0,

    @Column(name = "care_id")
    var careId : String = "",

    @Column(name = "infectious")
    var infectious : Boolean = false,

    @Column(name = "roomNumber")
    var roomNumber : Int = 0,
) {
}