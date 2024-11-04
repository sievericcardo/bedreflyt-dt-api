package no.uio.bedreflyt.api.model.simulation

import jakarta.persistence.*

@Entity
@Table(name = "patient_status")
class PatientStatusSim (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne
    @JoinColumn(name = "patient_id", referencedColumnName = "id")
    var patientId : PatientSim? = null,

    @Column(name = "infectious")
    var infectious : Boolean = false,

    @OneToOne
    @JoinColumn(name = "room_number", referencedColumnName = "id")
    var room: RoomSim? = null
)