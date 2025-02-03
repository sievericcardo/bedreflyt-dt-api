package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.Period

@Entity
@Table(name = "patient")
class Patient (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "patient_id")
    var patientId : String = "",

    @Column(name = "patient_name")
    var patientName : String = "",

    @Column(name = "patient_surname")
    var patientSurname : String = "",

    @Column(name = "patient_birthdate")
    var patientBirthdate : LocalDateTime? = null,

    @Column(name = "acute")
    var acute : Boolean = false,

    @Column(name = "gender")
    var gender : String = "",

    @Column(name = "Oslo")
    var oslo : Boolean = false,
) {
    fun computeAge(): Int {
        return if (patientBirthdate != null) {
            Period.between(patientBirthdate!!.toLocalDate(), LocalDateTime.now().toLocalDate()).years
        } else {
            0
        }
    }
}