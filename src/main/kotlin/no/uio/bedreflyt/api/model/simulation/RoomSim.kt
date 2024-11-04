package no.uio.bedreflyt.api.model.simulation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "room")
class RoomSim (
    @Id
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "room_description", unique = true)
    var roomDescription : String = ""
)
