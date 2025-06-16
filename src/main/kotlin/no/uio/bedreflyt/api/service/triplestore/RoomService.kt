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
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class RoomService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    @CachePut(value = ["rooms"], key = "#request.roomNumber + '_' + #request.ward + '_' + #request.hospital")
    open fun createRoom(request: RoomRequest) : TreatmentRoom? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                bedreflyt:Room${request.roomNumber}_${request.ward} rdf:type owl:NamedIndividual ,
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
                replConfig.regenerateSingleModel().invoke("rooms")

                return TreatmentRoom(request.roomNumber, request.capacity, 0.0,ward, hospital, monitoringCategory)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable(value = ["rooms"], key = "'allRooms'")
    open fun getAllRooms() : List<TreatmentRoom>? {
        lock.readLock().lock()
        try {
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
                    prog:MonitoringCategory_description ?category .
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
                val category = solution.get("?category").asLiteral().toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalName) ?: continue
                val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
                val monitoringCategory = monitoringCategoryService.getCategoryByDescription(category) ?: continue

                val room = TreatmentRoom(roomNumber, capacity, 0.0, ward, hospital, monitoringCategory)
                if (!rooms.any { it.roomNumber == room.roomNumber && it.treatmentWard.wardName == room.treatmentWard.wardName && it.hospital.hospitalCode == room.hospital.hospitalCode }) {
                    rooms.add(room)
                }
//                rooms.add(TreatmentRoom(roomNumber, capacity, ward, hospital, monitoringCategory))
            }

            return rooms
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable(value = ["rooms"], key = "#roomNumber")
    open fun getRoomByRoomNumber(roomNumber: Int): TreatmentRoom? {
        lock.readLock().lock()
        try {
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

            return TreatmentRoom(roomNumber, capacity, 0.0, ward, hospital, monitoringCategory)
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable(value = ["rooms"], key = "#roomNumber + '_' + #wardName + '_' + #hospitalCode")
    open fun getRoomByRoomNumberWardHospital(roomNumber: Int, wardName: String, hospitalCode: String) : TreatmentRoom? {
        lock.readLock().lock()
        try {
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

            return TreatmentRoom(roomNumber, capacity, 0.0, ward, hospital, monitoringCategory)
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable(value = ["rooms"], key = "'roomsByWardHospital_' + #wardName + '_' + #hospitalCode")
    open fun getRoomsByWardHospital(wardName: String, hospitalCode: String) : List<TreatmentRoom>? {
        lock.readLock().lock()
        try {
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

                val room = TreatmentRoom(roomNumber, capacity, 0.0, ward, hospital, monitoringCategory)
                if (!rooms.any { it.roomNumber == room.roomNumber && it.treatmentWard.wardName == room.treatmentWard.wardName && it.hospital.hospitalCode == room.hospital.hospitalCode }) {
                    rooms.add(room)
                }
            }

            return rooms
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["rooms"], key = "#room.roomNumber + '_' + #room.treatmentWard.wardName + '_' + #room.hospital.hospitalCode")
    @CachePut(value = ["rooms"], key = "#room.roomNumber + '_' + #newWard + '_' + #room.hospital.hospitalCode")
    open fun updateRoom(room: TreatmentRoom, newCapacity: Int, newWard: String, newCategory: String) : TreatmentRoom? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:Room${room.roomNumber}_${room.treatmentWard.wardName} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${room.monitoringCategory.description} ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName} ;
                    bedreflyt:hasCapacityNrBeds ${room.capacity} ;
                    bedreflyt:hasRoomNr ${room.roomNumber} .
            }
            INSERT {
                bedreflyt:Room${room.roomNumber}_${room.treatmentWard.wardName} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:$newCategory ;
                    bedreflyt:isAssignWard bedreflyt:$newWard ;
                    bedreflyt:hasCapacityNrBeds $newCapacity ;
                    bedreflyt:hasRoomNr ${room.roomNumber} .
            }
            WHERE {
                bedreflyt:Room${room.roomNumber}_${room.treatmentWard.wardName} rdf:type owl:NamedIndividual ,
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
                replConfig.regenerateSingleModel().invoke("rooms")
                val room = TreatmentRoom(room.roomNumber, newCapacity, 0.0,
                    wardService.getWardByNameAndHospital(newWard, room.hospital.hospitalCode) ?: return room,
                    hospitalService.getHospitalByCode(room.hospital.hospitalCode) ?: return room,
                    monitoringCategoryService.getCategoryByDescription(newCategory) ?: return room)

                return room
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict(value = ["rooms"], allEntries = true)
    open fun deleteRoom(room: TreatmentRoom) : Boolean {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:Room${room.roomNumber}_${room.treatmentWard.wardName} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
            }
            WHERE {
                bedreflyt:Room${room.roomNumber}_${room.treatmentWard.wardName} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName}  .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("rooms")

                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}