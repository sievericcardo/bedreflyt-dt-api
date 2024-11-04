package no.uio.bedreflyt.api.model.simulation

import jakarta.persistence.*

@Entity
@Table(name = "scenario")
class ScenarioSim (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "batch")
    var batch: Int = 0,

    @OneToOne
    @JoinColumn(name = "patientId", referencedColumnName = "id")
    var patientId: PatientSim? = null,

    @Column(name = "tratmentName")
    var treatmentName: String = ""
)