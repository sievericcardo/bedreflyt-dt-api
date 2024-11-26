package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.*
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class TriplestoreService (
    private val replConfig: REPLConfig
) {

    private val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    /*
     * Create operations
     */
    fun createDiagnosis(diagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :diagnosis_$diagnosisName a :Diagnosis ;
                    :diagnosisName "$diagnosisName" .
            }"""

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun createRoom(bedCategory: Long, roomDescription: String): Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "roomDescription" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun createRoomDistribution(roomNumber: Int, roomNumberModel: Int, room: Long, capacity: Int, bathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun createTask(taskName: String, averageDuration: Double, bed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
            }
        """

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun createTreatment(diagnosis: String, journeyOrder: Int, task: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /*
     * Read operations
     */
    fun getAllRooms() : List<Room>? {
        val rooms = mutableListOf<Room>()

        val query =
            """
               SELECT * WHERE {
                ?obj a prog:Room ;
                    prog:Room_bedCategory ?bedCategory ;
                    prog:Room_roomDescription ?roomDescription .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRooms.hasNext()) {
            return null
        }

        while (resultRooms.hasNext()) {
            val solution: QuerySolution = resultRooms.next()
            val roomId = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toLong()
            val roomDescription = solution.get("?roomDescription").asLiteral().toString()
            rooms.add(Room(roomId, roomDescription))
        }

        return rooms
    }


    fun getAllRoomDistributions() : List<RoomDistribution>? {
        val roomDistributions: MutableList<RoomDistribution> = mutableListOf()

        val query =  """
            SELECT * WHERE {
                ?obj a prog:RoomDistribution ;
                    prog:RoomDistribution_roomNumber ?roomNumber ;
                    prog:RoomDistribution_roomNumberModel ?roomNumberModel ;
                    prog:RoomDistribution_room ?room ;
                    prog:RoomDistribution_capacity ?capacity ;
                    prog:RoomDistribution_bathroom ?bathroom .
            }"""

        val resultRoomDistributions: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRoomDistributions.hasNext()) {
            return null
        }

        while (resultRoomDistributions.hasNext()) {
            val solution: QuerySolution = resultRoomDistributions.next()
            val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toInt()
            val roomNumberModel = solution.get("?roomNumberModel").asLiteral().toString().split("^^")[0].toInt()
            val room = solution.get("?room").asLiteral().toString().split("^^")[0].toLong()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val bathroom = solution.get("?bathroom").asLiteral().toString().split("^^")[0].toBoolean()
            roomDistributions.add(RoomDistribution(roomNumber, roomNumberModel, room, capacity, bathroom))
        }

        return roomDistributions
    }

    fun getAllTasks() : List<Task>? {
        val tasks: MutableList<Task> = mutableListOf()

        val query =
            """
           SELECT * WHERE {
            ?obj a prog:Task ;
                prog:Task_taskName ?taskName ;
                prog:Task_durationAverage ?averageDuration ;
                prog:Task_bed ?bedCategory .
        }"""

        val resultTasks: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTasks.hasNext()) {
            return null
        }

        while (resultTasks.hasNext()) {
            val solution: QuerySolution = resultTasks.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            val averageDuration = solution.get("?averageDuration").asLiteral().toString().split("^^")[0].toDouble()
            val bedCategory = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toInt()
            tasks.add(Task(taskName, averageDuration, bedCategory))
        }

        return tasks
    }

    fun getAllTreatments(): List<JourneyStep>? {
        val treatments: MutableList<JourneyStep> = mutableListOf()

        val query = """
        SELECT * WHERE {
            ?obj a prog:JourneyStep ;
                prog:JourneyStep_diagnosis ?diagnosis ;
                prog:JourneyStep_journeyOrder ?journeyOrder ;
                prog:JourneyStep_task ?task .
        }"""

        val resultTreatments: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTreatments.hasNext()) {
            return null
        }

        while (resultTreatments.hasNext()) {
            val solution: QuerySolution = resultTreatments.next()
            val diagnosis = solution.get("?diagnosis").asLiteral().toString()
            val journeyOrder = solution.get("?journeyOrder").asLiteral().toString().split("^^")[0].toInt()
            val task = solution.get("?task").asLiteral().toString()
            treatments.add(JourneyStep(diagnosis, journeyOrder, task))
        }

        return treatments
    }

    fun getAllDiagnosis(): List<Diagnosis>? {
        val diagnosis: MutableList<Diagnosis> = mutableListOf()

        val query = """
            SELECT * WHERE {
                ?obj a prog:Diagnosis ;
                    prog:Diagnosis_diagnosisName ?name .
            }"""

        val resultDiagnosis: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultDiagnosis.hasNext()) {
            return null
        }

        while (resultDiagnosis.hasNext()) {
            val solution: QuerySolution = resultDiagnosis.next()
            val name = solution.get("?name").asLiteral().toString()
            diagnosis.add(Diagnosis(name))
        }

        return diagnosis
    }

    /*
     * Update operations
     */
    fun updateDiagnosis(oldDiagnosisName: String, newDiagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :diagnosis_$oldDiagnosisName a :Diagnosis ;
                 :diagnosisName "$oldDiagnosisName" .
            }
            
            INSERT {
                :diagnosis_$newDiagnosisName a :Diagnosis ;
                 :diagnosisName "$newDiagnosisName" .
            }
            
            WHERE {
                :diagnosis_$oldDiagnosisName a :Diagnosis ;
                 :diagnosisName "$oldDiagnosisName" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun updateRoom(oldBedCategory: Long, oldRoomDescription: String, newBedCategory: Long, newRoomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$oldBedCategory a :Room ;
                    :bedCategory $oldBedCategory ;
                    :roomDescription "$oldRoomDescription" .
            }
            INSERT {
                :room$newBedCategory a :Room ;
                    :bedCategory $newBedCategory ;
                    :roomDescription "$newRoomDescription" .
            }
            WHERE {
                :room$oldBedCategory a :Room ;
                    :bedCategory $oldBedCategory ;
                    :roomDescription "$oldRoomDescription" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun updateRoomDistribution(oldRoomNumber: Int, oldRoomNumberModel: Int, oldRoom: Long, oldCapacity: Int, oldBathroom: Int,
                               newRoomNumber: Int, newRoomNumberModel: Int, newRoom: Long, newCapacity: Int, newBathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution$oldRoomNumber a :RoomDistribution ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :room $oldRoom ;
                    :capacity $oldCapacity ;
                    :bathroom $oldBathroom .
            }
            INSERT {
                :roomDistribution$newRoomNumber a :RoomDistribution ;
                    :roomNumber $newRoomNumber ;
                    :roomNumberModel $newRoomNumberModel ;
                    :room $newRoom ;
                    :capacity $newCapacity ;
                    :bathroom $newBathroom .
            }
            WHERE {
                :roomDistribution$oldRoomNumber a :RoomDistribution ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :room $oldRoom ;
                    :capacity $oldCapacity ;
                    :bathroom $oldBathroom .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun updateTask(oldTaskName: String, oldAverageDuration: Double, oldBed: Int, newTaskName: String, newAverageDuration: Double, newBed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_$oldTaskName :taskName "$oldTaskName" ;
                    :averageDuration $oldAverageDuration ;
                    :bed $oldBed .
            }
            INSERT {
                :task_$newTaskName a :Task ;
                    :taskName "$newTaskName" ;
                    :averageDuration $newAverageDuration ;
                    :bed $newBed .
            }
            WHERE {
                :task_$oldTaskName :taskName "$oldTaskName" ;
                    :averageDuration $oldAverageDuration ;
                    :bed $oldBed .
            }
        """

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun updateTreatment(oldDiagnosis: String, oldJourneyOrder: Int, oldTask: String, newDiagnosis: String, newJourneyOrder: Int, newTask: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${oldJourneyOrder}_$oldDiagnosis :diagnosis "$oldDiagnosis" ;
                    :journeyOrder $oldJourneyOrder ;
                    :task "$oldTask" .
            }
            INSERT {
                :journeyStep${newJourneyOrder}_$newDiagnosis a :JourneyStep ;
                    :diagnosis "$newDiagnosis" ;
                    :journeyOrder $newJourneyOrder ;
                    :task "$newTask" .
            }
            WHERE {
                :journeyStep${oldJourneyOrder}_$oldDiagnosis a :JourneyStep ;
                    :diagnosis "$oldDiagnosis" ;
                    :journeyOrder $oldJourneyOrder ;
                    :task "$oldTask" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /*
     * Delete operations
     */
    fun deleteDiagnosis(diagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :diagnosis_$diagnosisName a :Diagnosis ;
                 :diagnosisName "$diagnosisName" .
            }
            
            WHERE {
                :diagnosis_$diagnosisName a :Diagnosis ;
                 :diagnosisName "$diagnosisName" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteRoom(bedCategory: Long, roomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
            }
            WHERE {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteRoomDistribution (roomNumber: Int, roomNumberModel: Int, room: Long, capacity: Int, bathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
            WHERE {
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteTask(taskName: String, averageDuration: Double, bed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
            }
            WHERE {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
            }
        """

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteTreatment(diagnosis: String, journeyOrder: Int, task: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
            }
            WHERE {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}