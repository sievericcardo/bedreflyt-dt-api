package no.uio.bedreflyt.api.utils

import no.uio.bedreflyt.api.model.live.Patient
import no.uio.bedreflyt.api.model.live.PatientTrajectory
import no.uio.bedreflyt.api.service.live.PatientAllocationService
import no.uio.bedreflyt.api.service.live.PatientTrajectoryService
import no.uio.bedreflyt.api.types.DailyNeeds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.collections.forEach
import kotlin.text.contains
import kotlin.text.toLong
import kotlin.times

@Service
class AllocationHelper (
    private val patientTrajectoryService: PatientTrajectoryService,
    private val patientAllocationService: PatientAllocationService
) {

    val logger: Logger = LoggerFactory.getLogger(AllocationHelper::class.java)

    fun updatePatientNeeds(
        simulationNeeds: MutableList<DailyNeeds>,
        trajectories: List<PatientTrajectory>,
        patientNeeds: MutableMap<Patient, Long>,
        simulation: Boolean
    ) {
        // Pre-calculate maximum batch day to avoid list growth in loop
        val maxBatchDay = trajectories.maxOfOrNull { it.getBatchDay() } ?: -1

        // Ensure simulationNeeds has sufficient capacity upfront
        while (simulationNeeds.size <= maxBatchDay) {
            simulationNeeds.add(mutableListOf())
        }

        // Build existing entries map for faster lookup
        val existingEntries = simulationNeeds.flatMapIndexed { index, dailyNeeds ->
            dailyNeeds.map { (patientId, _) -> "${index}_${patientId.patientId}" }
        }.toSet()

        // Process trajectories and collect new trajectories for batch insert
        val newTrajectories = mutableListOf<PatientTrajectory>()

        trajectories.forEach { trajectory ->
            val batchDay = trajectory.getBatchDay()
            val key = "${batchDay}_${trajectory.patientId.patientId}"

            if (key !in existingEntries) {
                simulationNeeds[batchDay].add(Pair(trajectory.patientId, trajectory.need))
            }
        }

        simulationNeeds.forEachIndexed { index, dailyNeeds ->
            dailyNeeds.forEach { (patient, need) ->
                patientNeeds[patient] = patientNeeds.getOrDefault(patient, 0L) + need.toLong()
                val trajectory = PatientTrajectory(
                    patientId = patient,
                    date = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0),
                    need = need,
                    simulated = simulation
                )

                trajectory.date = if (simulation) {
                    trajectory.setDate(index * 24).withMinute(0).withSecond(0).withNano(0)
                } else {
                    trajectory.setDate(index).withMinute(0).withSecond(0).withNano(0)
                }
                newTrajectories.add(trajectory)
            }
        }

        // Batch save all trajectories at once
        if (newTrajectories.isNotEmpty()) {
            patientTrajectoryService.saveAllPatientTrajectories(newTrajectories)
        }
    }

    fun updateAllocations(patientNeeds: Map<Patient, Long>, offset: Long = 0, simulation: Boolean) {
        if (patientNeeds.isEmpty()) return

        // Batch fetch all relevant allocations
        val patientIds = patientNeeds.keys.toList()
        val allocations = patientAllocationService.findByPatientIds(patientIds, simulation) ?: return

        // Update allocations in memory
        val updatedAllocations = allocations.mapNotNull { allocation ->
            val need = patientNeeds[allocation.patientId]
            if (need != null) {
                allocation.apply {
                    dueDate = LocalDateTime.now().plusDays((need / 24).toInt() + offset).withMinute(0).withSecond(0).withNano(0)
                }
            } else null
        }

        // Batch update all allocations
        if (updatedAllocations.isNotEmpty()) {
            patientAllocationService.updateAllPatientAllocations(updatedAllocations)
        }
    }

    fun handleEmptyAllocations(incomingPatients: MutableList<Pair<Patient, String>>) {
        incomingPatients.forEach { patient ->
            val patientTrajectory = patientTrajectoryService.findByPatientId(patient.first)
            patientTrajectory?.forEach { trajectory ->
                patientTrajectoryService.deletePatientTrajectory(trajectory)
            }
            val patientAllocation = patientAllocationService.findByPatientId(patient.first)
            if (patientAllocation != null) {
                patientAllocationService.deletePatientAllocation(patientAllocation)
            }
        }
    }

    fun removeUnusedAllocations(needs: DailyNeeds) {
        val allocations = patientAllocationService.findAll() ?: return
        allocations.forEach { allocation ->
            if (needs.none { it.first == allocation.patientId} || needs.any { it.first == allocation.patientId && it.second == 0 }) {
                allocation.roomNumber = -1
                patientAllocationService.updatePatientAllocation(allocation)
            }
        }
    }

    fun cleanAllocations() {
        val allocation = patientAllocationService.findAll()
        allocation?.forEach { patientAllocation ->
            if (patientAllocation.diagnosisCode == "" && patientAllocation.diagnosisName == "") {
                cleanTrajectories(patientAllocation.patientId, patientAllocation.simulated)
                patientAllocationService.deletePatientAllocation(patientAllocation)
            }
        }
    }

    fun cleanTrajectories(patientId: Patient, simulated: Boolean) {
//        val trajectories = patientTrajectoryService.findByPatientId(patientId)
        patientTrajectoryService.deleteTrajectoryByPatient(patientId)
//        trajectories?.forEach { trajectory ->
//            patientTrajectoryService.deletePatientTrajectory(trajectory)
//        }
    }
}