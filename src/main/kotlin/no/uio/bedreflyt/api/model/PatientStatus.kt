package no.uio.bedreflyt.api.model

import jakarta.persistence.*

@Entity
@Table(name = "patient_status")
class PatientStatus (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "id")
    var patientId : Patient? = null,

    @Column(name = "infectious")
    var infectious : Boolean = false,

    @OneToOne
    @JoinColumn(name = "room_number", referencedColumnName = "id")
    var room: Room? = null
)