package no.uio.bedreflyt.api.types

import no.uio.bedreflyt.api.model.live.Patient

data class SimulationRequest(
    val scenario: List<ScenarioRequest>,
    val mode: String,
    val smtMode: String = "changes"
)

data class RoomInfo(
    val patients: List<Patient>,
    val gender: String
)

typealias SingleRoom = String

typealias Allocation = Map<SingleRoom, RoomInfo?>

data class SimulationResponse(
    val allocations: List<List<Allocation>>,
    val changes: Int
)

data class ScenarioRequest(
    val batch: Int,
    val patientId: String?,
    val diagnosis: String?
)

data class SolverRequest(
    val no_rooms: Int,
    val capacities: List<Int>,
    val room_distances: List<Long>,
    val no_patients: Int,
    val genders: List<Boolean>,
    val infectious: List<Boolean>,
    val patient_distances: List<Int>,
    val previous: List<Int>,
    // options are c[hanges] to minimize number of room changes or
    // m[ax] to minimize maximum number of patients per room
    val mode: String
)

data class GlobalSolverRequest(
    val capacities: List<Int>,
    val room_distances: List<Long>,
    val genders: Map<String, Boolean>, // mapping from patientIds to gender (isMale)
    val infectious: Map<String, Boolean>, // patientId -> infectious
    val patient_distances: List<Map<String, Int>>, // patient_distances[i][j] = c means patient j is in category c on day i
    // options are c[hanges] to minimize number of room changes or
    // m[ax] to minimize maximum number of patients per room
    val mode: String
)

data class SolverResponse(
    val allocations: List<Allocation>,
    val changes: Int
)

// request for monte carlo sim
// the naming is a bit awkward since "SimulationRequest/Response" already exists
data class MultiSimulationRequest(
    val scenario: List<ScenarioRequest>,
    val repetitions: Int = 10,
    val risk: Double = 0.5,
    val smtMode: String = "changes"
)

data class MultiSimulationResponse(
    val runs: Int,
    val results: List<SimulationResponse>
)

data class DiagnosisRequest (
    val diagnosisName : String,
)

data class UpdateDiagnosisRequest (
    val oldDiagnosisName : String,
    val newDiagnosisName : String,
)

data class RoomRequest (
    val roomNumber: Int,
    val roomNumberModel: Int,
    val room: Long,
    val capacity: Int,
    val bathroom: Boolean
)

data class UpdateRoomRequest (
    val oldRoomNumber: Int,
    val oldRoomNumberModel: Int,
    val oldRoom: Long,
    val oldCapacity: Int,
    val oldBathroom: Boolean,
    val newRoomNumber: Int,
    val newRoomNumberModel: Int,
    val newRoom: Long,
    val newCapacity: Int,
    val newBathroom: Boolean
)

data class RoomCategoryRequest (
    val bedCategory: Long,
    val roomDescription: String
)

data class UpdateRoomCategoryRequest (
    val oldBedCategory: Long,
    val oldRoomDescription: String,
    val newBedCategory: Long,
    val newRoomDescription: String
)

data class TaskRequest (
    val taskName : String,
    val averageDuration: Double,
    val bed: Int
)

data class UpdateTaskRequest (
    val oldTaskName: String,
    val newTaskName: String,
    val oldAverageDuration: Double,
    val newAverageDuration: Double,
    val oldBed: Int,
    val newBed: Int
)

data class TaskDependencyRequest (
    val treatment: String,
    val diagnosis: String,
    val taskName: String,
    val dependsOn: String
)

data class UpdateTaskDependencyRequest (
    val treatment: String,
    val diagnosis: String,
    val taskName: String,
    val oldDependsOn: String,
    val newDependsOn: String
)

data class DeleteTaskDependencyRequest (
    val treatment: String,
    val diagnosis: String,
    val taskName: String,
    val dependsOn: String
)

data class TreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val frequency: Double,
    val weight: Double
)

data class UpdateTreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val oldFrequency: Double,
    val oldWeight: Double,
    val newFrequency: Double,
    val newWeight: Double
)

data class DeteleTreatmentRequest (
    val treatmentId: String,
    val diagnosis: String,
    val frequency: Double,
    val weight: Double
)
