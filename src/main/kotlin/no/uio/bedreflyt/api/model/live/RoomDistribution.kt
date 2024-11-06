package no.uio.bedreflyt.api.model.live

import jakarta.persistence.*

@Entity
@Table(name = "room_distribution")
class RoomDistribution (
    @Id
    var roomNumber : Long = 0,

    @Column(name = "room_number_model")
    var roomNumberModel : Long = 0,

    @ManyToOne
    @JoinColumn(name = "bed_category", referencedColumnName = "id")
    var room: Room? = null,

    @Column(name = "capacity")
    var capacity : Int = 0,

    @Column(name = "bathroom")
    var bathroom : Boolean = false
)