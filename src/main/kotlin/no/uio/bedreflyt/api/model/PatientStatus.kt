package no.uio.bedreflyt.api.model

import jakarta.persistence.*

@Entity
@Table(name = "patient_status")
class PatientStatus {
    @Id
    @OneToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "patient_id")
    var patientId : String = ""

    @Column(name = "infectious")
    var infectious : Boolean = false

    @OneToOne
    @JoinColumn(name = "room_number", referencedColumnName = "id")
    var room: Room? = null
}