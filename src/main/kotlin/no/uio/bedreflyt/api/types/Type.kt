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
typealias DailyNeeds = MutableList<Pair<Patient, Int>>
typealias SimulationNeeds = List<DailyNeeds>
typealias AllocationRequest = SimulationRequest
typealias AllocationResponse = SimulationResponse

data class SimulationResponse(
    val allocations: List<List<Allocation>>,
    val changes: Int
)

data class ScenarioRequest(
    val batch: Int,
    val patientId: String,
    val diagnosis: String
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
    val contagious: Map<String, Boolean>, // patientId -> contagious
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
    val roomNumber: Int,
    val newRoomNumberModel: Int?,
    val newRoom: Long?,
    val newCapacity: Int?,
    val newBathroom: Boolean?
)

data class DeleteRoomRequest (
    val roomNumber: Int
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
    val taskName: String,
    val newAverageDuration: Double?,
    val newBed: Int?
)

data class DeleteTaskRequest (
    val taskName: String
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
    val newFrequency: Double?,
    val newWeight: Double?
)

data class DeteleTreatmentRequest (
    val treatmentId: String,
    val diagnosis: String
)

data class PatientRequest (
    val patientName: String,
    val patientSurname: String,
    val patientAddress: String,
    val city: String,
    val patientBirthdate: String,
    val gender: String
)

data class UpdatePatientRequest (
    val patientId: String,
    val patientName: String?,
    val patientSurname: String?,
    val patientAddress: String?,
    val city: String?,
    val patientBirthdate: String?,
    val gender: String?
)

data class DeletePatientRequest (
    val patientId: String
)

data class PatientAllocationRequest (
    val patientId: String,
    val acute: Boolean,
    val diagnosisCode: String,
    val diagnosisName: String,
    val acuteCategory: Int?,
    val careCategory: Int?,
    val monitoringCategory: Int?,
    val careId: Int?,
    val contagious: Boolean,
    val roomNumber: Int?
)

data class UpdatePatientAllocationRequest (
    val patientId: String,
    val newAcute: Boolean?,
    val newDiagnosisCode: String?,
    val newDiagnosisName: String?,
    val newAcuteCategory: Int?,
    val newCareCategory: Int?,
    val newMonitoringCategory: Int?,
    val newCareId: Int?,
    val newContagious: Boolean?,
    val newRoomNumber: Int?
)

data class DeletePatientAllocationRequest (
    val patientId: String
)
