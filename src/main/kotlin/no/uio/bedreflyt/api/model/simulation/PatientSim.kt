package no.uio.bedreflyt.api.model.simulation

import jakarta.persistence.*

enum class Gender {
    MALE, FEMALE
}

@Entity
@Table(name = "patient")
class PatientSim (
    @Id
    @Column(name = "id")
    var patientId : String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    var gender : Gender? = null,

    @Column(name = "gender_model")
    var genderModel : Boolean = false
) {
    @get:Transient
    val isMale: Boolean
        get() = gender == Gender.MALE

    fun isMaleGender(): Boolean {
        return isMale
    }
}