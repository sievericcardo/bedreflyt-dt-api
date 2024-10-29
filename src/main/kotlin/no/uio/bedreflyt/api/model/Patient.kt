package no.uio.bedreflyt.api.model

import jakarta.persistence.*

enum class Gender {
    MALE, FEMALE
}

@Entity
@Table(name = "patient")
class Patient {
    @Id
    @Column(name = "id")
    var patientId : String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    var gender : Gender? = null

    @get:Transient
    val isMale: Boolean
        get() = gender == Gender.MALE

    fun isMaleGender(): Boolean {
        return isMale
    }
}