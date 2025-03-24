package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Room
import no.uio.bedreflyt.api.model.triplestore.TreatmentRoom
import no.uio.bedreflyt.api.types.DeleteRoomRequest
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

@Service
class RoomService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("rooms", key = "#request.roomNumber + '_' + #request.ward + '_' + #request.hospital")
    fun createRoom(request: RoomRequest) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Cacheable("rooms")
    fun getAllRooms() : List<TreatmentRoom>? {
        val rooms: MutableList<TreatmentRoom> = mutableListOf()

        val query =  """
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

        return rooms
    }

    @Cacheable("rooms")
    fun getRoomByRoomNumber(roomNumber: Int): TreatmentRoom? {
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

    @Cacheable("rooms", key = "#roomNumber + '_' + #wardName + '_' + #hospitalCode")
    fun getRoomByRoomNumberWardHospital(roomNumber: Int, wardName: String, hospitalCode: String) : TreatmentRoom? {
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

    @CacheEvict("rooms", key = "#room.roomNumber + '_' + #room.ward.wardName + '_' + #room.hospital.hospitalCode")
    @CachePut("rooms", key = "#room.roomNumber + '_' + #newWard + '_' + #room.hospital.hospitalCode")
    fun updateRoom(room: TreatmentRoom, newCapacity: Int?, newWard: String?, newCategory: String?) : Boolean {
        val capacity = newCapacity ?: room.capacity
        val ward = newWard ?: room.treatmentWard.wardName
        val category = newCategory?.split(" ")?.joinToString(" ") ?: room.monitoringCategory.description

        val query = """
            PREFIX bedreflyt: <$prefix>
            
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
                    bedreflyt:hasMonitoringStatus bedreflyt:$category ;
                    bedreflyt:isAssignWard bedreflyt:$ward ;
                    bedreflyt:hasCapacityNrBeds $capacity ;
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @CacheEvict("rooms", key = "#room.roomNumber + '_' + #room.ward.wardName + '_' + #room.hospital.hospitalCode")
    fun deleteRoom(room: TreatmentRoom) : Boolean {

        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:Room${room.roomNumber} rdf:type owl:NamedIndividual ,
                           <https://w3id.org/rec/building/TreatmentRoom> ;
                    bedreflyt:hasBathroom bedreflyt:Room${room.roomNumber}-1 ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${room.monitoringCategory.description} ;
                    bedreflyt:isAssignWard bedreflyt:${room.treatmentWard.wardName} ;
                    bedreflyt:hasCapacityNrBeds ${room.capacity} ;
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
            return true
        } catch (e: Exception) {
            return false
        }
    }
}