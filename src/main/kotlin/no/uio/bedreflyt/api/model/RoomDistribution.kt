package no.uio.bedreflyt.api.model

import jakarta.persistence.*

@Entity
@Table(name = "room_distribution")
class RoomDistribution {
    @Id
    var roomNumber : Int = 0

    @Column(name = "room_number_model", unique = true)
    var roomNumberModel : Int = 0

    @ManyToOne
    @JoinColumn(name = "bed_category", referencedColumnName = "id")
    var room: Room? = null

    @Column(name = "capacity")
    var capacity : Int = 0

    @Column(name = "bathroom")
    var bathroom : Int = 0
}