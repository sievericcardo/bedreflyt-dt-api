package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.Period
import java.util.UUID

@Entity
@Table(name = "patient")
class Patient (
    @Id
    @Column(name = "patient_id", unique = true)
    var patientId : String = UUID.randomUUID().toString(),

    @Column(name = "patient_name")
    var patientName : String = "",

    @Column(name = "patient_surname")
    var patientSurname : String = "",

    @Column(name = "patient_address")
    var patientAddress : String = "",

    @Column(name = "patient_city")
    var city : String = "",

    @Column(name = "patient_birthdate")
    var patientBirthdate : LocalDateTime? = null,

    @Column(name = "gender")
    var gender : String = "",
) {
    fun computeAge(): Int {
        return if (patientBirthdate != null) {
            Period.between(patientBirthdate!!.toLocalDate(), LocalDateTime.now().toLocalDate()).years
        } else {
            0
        }
    }
}