package no.uio.bedreflyt.api.model.live

import com.fasterxml.jackson.annotation.JsonGetter
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
    @JsonGetter("patientBirthDate")
    fun getPatientBirthDateForJson(): LocalDateTime {
        return patientBirthdate ?: LocalDateTime.of(1970, 1, 1, 0, 0)
    }

    fun computeAge(): Int {
        return if (patientBirthdate != null) {
            Period.between(patientBirthdate!!.toLocalDate(), LocalDateTime.now().toLocalDate()).years
        } else {
            0
        }
    }

    override fun toString(): String {
        return "Patient(patientId='$patientId', name='$patientName $patientSurname', address='$patientAddress', city='$city', birthdate=$patientBirthdate, gender='$gender')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Patient) return false
        return patientId == other.patientId
    }

    override fun hashCode(): Int {
        return patientId.hashCode()
    }
}