package no.uio.bedreflyt.api.model

import jakarta.persistence.*
import jakarta.persistence.Transient

@Entity
@Table(name = "scenario")
class Scenario (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "batch")
    var batch: Int = 0,

    @OneToOne
    @JoinColumn(name = "patientId", referencedColumnName = "id")
    var patientId: Patient? = null,

    @Column(name = "tratmentName")
    var treatmentName: String = ""
)