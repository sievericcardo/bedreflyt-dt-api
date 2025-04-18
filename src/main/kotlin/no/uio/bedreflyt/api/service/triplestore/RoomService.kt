package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.TreatmentRoom
import no.uio.bedreflyt.api.types.RoomRequest
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
open class RoomService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    open fun createRoom(request: RoomRequest) : TreatmentRoom? {
        synchronized(lock) {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                bedreflyt:Room${request.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${request.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${request.categoryDescription} ;
                    bedreflyt:isAssignWard bedreflyt:${request.ward} ;
                    bedreflyt:hasCapacityNrBeds ${request.capacity} ;
                    bedreflyt:hasRoomNr ${request.roomNumber} .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                val ward = wardService.getWardByNameAndHospital(request.ward, request.hospital) ?: return null
                val hospital = hospitalService.getHospitalByCode(request.hospital) ?: return null
                val monitoringCategory = monitoringCategoryService.getCategoryByDescription(request.categoryDescription) ?: return null

                // Explicitly update the cache
                val treatmentRoom = TreatmentRoom(request.roomNumber, request.capacity, ward, hospital, monitoringCategory)
                cacheManager.getCache("rooms")?.put(request.roomNumber, treatmentRoom)
                return treatmentRoom
            } catch (e: Exception) {
                return null
            }
        }
    }

    open fun getAllRooms() : List<TreatmentRoom>? {
        synchronized(lock) {
            val rooms: MutableList<TreatmentRoom> = mutableListOf()

            val query = """
            SELECT DISTINCT ?roomNumber ?capacity ?wardName ?hospitalName ?category WHERE {
                ?obj a prog:TreatingRoom ;
                    prog:TreatingRoom_roomNumber ?roomNumber ;
                    prog:TreatingRoom_capacity ?capacity ;
                    prog:TreatingRoom_treatingWard ?ward ;
                    prog:TreatingRoom_hospital ?hospital ;
                    prog:TreatingRoom_monitoringCategory ?monitoringCategory .
                ?ward a prog:Ward ;
                    prog:Ward_wardName ?wardName .
                ?hospital a prog:Hospital ;
                    prog:Hospital_hospitalCode ?hospitalName .
                ?monitoringCategory a prog:MonitoringCategory ;
                    prog:MonitoringCategory_category ?category .
            }"""

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            while (resultRooms.hasNext()) {
                val solution: QuerySolution = resultRooms.next()
                val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toInt()
                val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
                val wardName = solution.get("?wardName").asLiteral().toString()
                val hospitalName = solution.get("?hospitalName").asLiteral().toString()
                val category = solution.get("?category").asLiteral().toString().split("^^")[0].toInt()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalName) ?: continue
                val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
                val monitoringCategory = monitoringCategoryService.getCategoryByCategory(category) ?: continue

                rooms.add(TreatmentRoom(roomNumber, capacity, ward, hospital, monitoringCategory))
            }

            // Explicitly update the cache
            cacheManager.getCache("rooms")?.put("allRooms", rooms)
            return rooms
        }
    }

    open fun getRoomByRoomNumber(roomNumber: Int): TreatmentRoom? {
        synchronized(lock) {
            val query = """
            SELECT DISTINCT ?capacity ?wardName ?hospitalName ?category WHERE {
                ?obj a prog:TreatingRoom ;
                    prog:TreatingRoom_roomNumber $roomNumber ;
                    prog:TreatingRoom_capacity ?capacity ;
                    prog:TreatingRoom_treatingWard ?ward ;
                    prog:TreatingRoom_hospital ?hospital ;
                    prog:TreatingRoom_monitoringCategory ?monitoringCategory .
                ?ward a prog:Ward ;
                    prog:Ward_wardName ?wardName .
                ?hospital a prog:Hospital ;
                    prog:Hospital_hospitalCode ?hospitalName .
                ?monitoringCategory a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?category .
            }
        """.trimIndent()

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            val solution: QuerySolution = resultRooms.next()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val wardName = solution.get("?wardName").asLiteral().toString()
            val hospitalName = solution.get("?hospitalName").asLiteral().toString()
            val category = solution.get("?category").asLiteral().toString()

            val ward = wardService.getWardByNameAndHospital(wardName, hospitalName) ?: return null
            val hospital = hospitalService.getHospitalByCode(hospitalName) ?: return null
            val monitoringCategory = monitoringCategoryService.getCategoryByDescription(category) ?: return null

            return TreatmentRoom(roomNumber, capacity, ward, hospital, monitoringCategory)
        }
    }

    open fun getRoomByRoomNumberWardHospital(roomNumber: Int, wardName: String, hospitalCode: String) : TreatmentRoom? {
        synchronized(lock) {
            val query = """
            SELECT DISTINCT ?capacity ?category WHERE {
                ?obj a prog:TreatingRoom ;
                    prog:TreatingRoom_roomNumber $roomNumber ;
                    prog:TreatingRoom_capacity ?capacity ;
                    prog:TreatingRoom_treatingWard ?ward ;
                    prog:TreatingRoom_hospital ?hospital ;
                    prog:TreatingRoom_monitoringCategory ?monitoringCategory .
                ?ward a prog:Ward ;
                    prog:Ward_wardName "$wardName" .
                ?hospital a prog:Hospital ;
                    prog:Hospital_hospitalCode "$hospitalCode" .
                ?monitoringCategory a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?category .
            }
        """.trimIndent()

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            val solution: QuerySolution = resultRooms.next()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val category = solution.get("?category").asLiteral().toString()

            val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return null
            val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return null
            val monitoringCategory = monitoringCategoryService.getCategoryByDescription(category) ?: return null

            return TreatmentRoom(roomNumber, capacity, ward, hospital, monitoringCategory)
        }
    }

    open fun getRoomsByWardHospital(wardName: String, hospitalCode: String) : List<TreatmentRoom>? {
        synchronized(lock) {
            val rooms = mutableListOf<TreatmentRoom>()
            val query = """
                SELECT DISTINCT ?roomNumber ?capacity ?category WHERE {
                    ?obj a prog:TreatingRoom ;
                        prog:TreatingRoom_roomNumber ?roomNumber ;
                        prog:TreatingRoom_capacity ?capacity ;
                        prog:TreatingRoom_treatingWard ?ward ;
                        prog:TreatingRoom_hospital ?hospital ;
                        prog:TreatingRoom_monitoringCategory ?monitoringCategory .
                    ?ward a prog:Ward ;
                        prog:Ward_wardName "$wardName" .
                    ?hospital a prog:Hospital ;
                        prog:Hospital_hospitalCode "$hospitalCode" .
                    ?monitoringCategory a prog:MonitoringCategory ;
                        prog:MonitoringCategory_description ?category .
                }
            """.trimIndent()

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            while (resultRooms.hasNext()) {
                val solution: QuerySolution = resultRooms.next()
                val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toInt()
                val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
                val category = solution.get("?category").asLiteral().toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return null
                val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return null
                val monitoringCategory = monitoringCategoryService.getCategoryByDescription(category) ?: return null

                rooms.add(TreatmentRoom(roomNumber, capacity, ward, hospital, monitoringCategory))
            }

            return rooms
        }
    }

    open fun updateRoom(room: TreatmentRoom, newCapacity: Int, newWard: String, newCategory: String) : TreatmentRoom? {
        synchronized(lock) {

            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${room.monitoringCategory.description} ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName} ;
                    bedreflyt:hasCapacityNrBeds ${room.capacity} ;
                    bedreflyt:hasRoomNr ${room.roomNumber} .
            }
            INSERT {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:$newCategory ;
                    bedreflyt:isAssignWard bedreflyt:$newWard ;
                    bedreflyt:hasCapacityNrBeds $newCapacity ;
                    bedreflyt:hasRoomNr ${room.roomNumber} .
            }
            WHERE {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${room.monitoringCategory.description} ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName} ;
                    bedreflyt:hasCapacityNrBeds ${room.capacity} ;
                    bedreflyt:hasRoomNr ${room.roomNumber} .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                return TreatmentRoom(room.roomNumber, newCapacity,
                    wardService.getWardByNameAndHospital(newWard, room.hospital.hospitalCode) ?: return room,
                    hospitalService.getHospitalByCode(room.hospital.hospitalCode) ?: return room,
                    monitoringCategoryService.getCategoryByDescription(newCategory) ?: return room)
            } catch (e: Exception) {
                return null
            }
        }
    }

    open fun deleteRoom(room: TreatmentRoom) : Boolean {
        synchronized(lock) {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
            }
            WHERE {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName}  .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                // Explicitly clear the cache
                cacheManager.getCache("rooms")?.clear()
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }
}