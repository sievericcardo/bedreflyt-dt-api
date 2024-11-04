package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.model.simulation.ScenarioSim
import no.uio.bedreflyt.api.repository.simulation.ScenarioSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ScenarioSimService @Autowired constructor(
    private val scenarioSimRepository: ScenarioSimRepository
) {
    fun findAll(): MutableList<ScenarioSim?> {
        return scenarioSimRepository.findAll()
    }

    fun findByBatch (batch: Int, sqliteDbUrl: String? = null): ScenarioSim {
        return scenarioSimRepository.findByBatch(batch)
    }

    fun findByPatientId(patientId: PatientSim, sqliteDbUrl: String? = null): ScenarioSim {
        return scenarioSimRepository.findByPatientId(patientId)
    }

    fun findByTreatmentName(treatmentName: String, sqliteDbUrl: String? = null): ScenarioSim {
        return scenarioSimRepository.findByTreatmentName(treatmentName)
    }

    fun saveScenario(scenarioSim: ScenarioSim, sqliteDbUrl: String? = null): ScenarioSim {
        return scenarioSimRepository.save(scenarioSim)
    }
}