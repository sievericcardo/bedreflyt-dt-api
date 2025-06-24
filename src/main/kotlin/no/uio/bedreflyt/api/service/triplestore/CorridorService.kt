package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Corridor
import no.uio.bedreflyt.api.types.CorridorRequest
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class CorridorService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService,
    private val cacheManager: org.springframework.cache.CacheManager
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val log: Logger = LoggerFactory.getLogger(CorridorService::class.java.name)
    private val lock = ReentrantReadWriteLock()

    @CacheEvict(value = ["corridors"], allEntries = true)
    @CachePut(value = ["corridors"], key = "#corridorRequest.roomNumber + '_' + #corridorRequest.ward + '_' + #corridorRequest.hospital")
    open fun createCorridor(corridorRequest: CorridorRequest): Corridor? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            INSERT DATA {
                bedreflyt:${corridorRequest.hospital}_${corridorRequest.ward}_Corridor${corridorRequest.roomNumber} a bedreflyt:Corridor ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${corridorRequest.categoryDescription} ;
                    bedreflyt:isAssignWard bedreflyt:${corridorRequest.ward} ;
                    bedreflyt:penalty "${corridorRequest.penalty}"^^xsd:double ;
                    bedreflyt:hasCapacityNrBeds ${corridorRequest.capacity} ;
                    bedreflyt:hasRoomNr ${corridorRequest.roomNumber} ;
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()

                val ward = wardService.getWardByNameAndHospital(corridorRequest.ward, corridorRequest.hospital)!!
                val hospital = hospitalService.getHospitalByCode(corridorRequest.hospital)!!
                val category = monitoringCategoryService.getCategoryByDescription(corridorRequest.categoryDescription)!!

                replConfig.regenerateSingleModel().invoke("offices")

                log.info("Office created successfully: ${corridorRequest.roomNumber} in ${corridorRequest.ward} at ${corridorRequest.hospital}")
                return Corridor(
                    corridorRequest.roomNumber,
                    corridorRequest.capacity,
                    corridorRequest.penalty,
                    ward,
                    hospital,
                    category
                )
            } catch (_: Exception) {
                log.error("Error creating office: ${corridorRequest.roomNumber} in ${corridorRequest.ward} at ${corridorRequest.hospital}")
                return null
            }
        } catch (e: Exception) {
            log.error("Error creating office: $e")
            return null
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable(value = ["corridors"], key = "'allCorridors'")
    open fun getAllCorridors(): List<Corridor>? {
        lock.readLock().lock()
        try {
            val corridors: MutableList<Corridor> = mutableListOf()

            val query = """
            SELECT ?roomNumber ?capacity ?penalty ?available ?wardName ?hospitalCode ?categoryDescription WHERE {
                ?corridor a prog:Corridor ;
                    prog:Corridor_roomNumber ?roomNumber ;
                    prog:Corridor_capacity ?capacity ;
                    prog:Corridor_penalty ?penalty ;
                    prog:Corridor_corridorWard ?wardObj ;
                    prog:Corridor_hospital ?hospitalObj ;
                    prog:Corridor_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName ?wardName .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode ?hospitalCode .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }"""

            val resultSet = repl.interpreter!!.query(query)!!

            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val solution = resultSet.nextSolution()
                val roomNumber = solution.get("roomNumber").toString().split("^^")[0].toInt()
                val capacity = solution.get("capacity").asLiteral().toString().split("^^")[0].toInt()
                val penalty = solution.get("penalty").asLiteral().toString().split("^^")[0].toDouble()
                val wardName = solution.get("wardName").toString()
                val hospitalCode = solution.get("hospitalCode").toString()
                val categoryDescription = solution.get("categoryDescription").toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode)!!
                val hospital = hospitalService.getHospitalByCode(hospitalCode)!!
                val category = monitoringCategoryService.getCategoryByDescription(categoryDescription)!!

                val corridor = Corridor(roomNumber, capacity, penalty, ward, hospital, category)
                if (!corridors.any { it.roomNumber == corridor.roomNumber && it.treatmentWard.wardName == corridor.treatmentWard.wardName && it.hospital.hospitalCode == corridor.hospital.hospitalCode }) {
                    corridors.add(corridor)
                }
            }

            return corridors
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("corridors", key = "#roomNumber + '_' + #ward + '_' + #hospital")
    open fun getCorridorByRoonNumberWardHospital (roomNumber: Int, wardName: String, hospitalCode: String): Corridor? {
        lock.readLock().lock()
        try {
            val query = """
            SELECT ?capacity ?penalty ?wardName ?hospitalCode ?categoryDescription WHERE {
                ?corridor a prog:Corridor ;
                    prog:Corridor_roomNumber $roomNumber ;
                    prog:Corridor_capacity ?capacity ;
                    prog:Corridor_penalty ?penalty ;
                    prog:Corridor_corridorWard ?wardObj ;
                    prog:Corridor_hospital ?hospitalObj ;
                    prog:Corridor_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName "$wardName" .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode "$hospitalCode" .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }"""

            val resultSet = repl.interpreter!!.query(query) ?: return null

            if (!resultSet.hasNext()) {
                return null
            }

            val solution = resultSet.nextSolution()
            val capacity = solution.get("capacity").asLiteral().toString().split("^^")[0].toInt()
            val penalty = solution.get("penalty").asLiteral().toString().split("^^")[0].toDouble()
            val categoryDescription = solution.get("categoryDescription").toString()

            val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode)!!
            val hospital = hospitalService.getHospitalByCode(hospitalCode)!!
            val category = monitoringCategoryService.getCategoryByDescription(categoryDescription)!!

            return Corridor(roomNumber, capacity, penalty, ward, hospital, category)
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("corridors", key = "#wardName + '_' + #hospitalCode")
    open fun getCorridorByWardHospital (wardName: String, hospitalCode: String): List<Corridor>? {
        lock.readLock().lock()
        try {
            val corridors = mutableListOf<Corridor>()

            val query = """
            SELECT ?roomNumber ?capacity ?penalty ?categoryDescription WHERE {
                ?corridor a prog:Corridor ;
                    prog:Corridor_corridorWard ?wardObj ;
                    prog:Corridor_hospital ?hospitalObj ;
                    prog:Corridor_roomNumber ?roomNumber ;
                    prog:Corridor_capacity ?capacity ;
                    prog:Corridor_penalty ?penalty ;
                    prog:Corridor_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName "$wardName" .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode "$hospitalCode" .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }"""

            val resultSet = repl.interpreter!!.query(query) ?: return null

            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val solution = resultSet.nextSolution()
                val roomNumber = solution.get("roomNumber").toString().split("^^")[0].toInt()
                val capacity = solution.get("capacity").asLiteral().toString().split("^^")[0].toInt()
                val penalty = solution.get("penalty").asLiteral().toString().split("^^")[0].toDouble()
                val categoryDescription = solution.get("categoryDescription").toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode)!!
                val hospital = hospitalService.getHospitalByCode(hospitalCode)!!
                val category = monitoringCategoryService.getCategoryByDescription(categoryDescription)!!

                val corridor = Corridor(roomNumber, capacity, penalty, ward, hospital, category)
                if (!corridors.any { it.roomNumber == corridor.roomNumber && it.treatmentWard.wardName == corridor.treatmentWard.wardName && it.hospital.hospitalCode == corridor.hospital.hospitalCode }) {
                    corridors.add(corridor)
                }
            }

            return corridors
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["corridors"], allEntries = true)
    @CachePut(value = ["corridors"], key = "#corridor.roomNumber + '_' + #corridor.treatmentWard.wardName + '_' + #corridor.hospital.hospitalCode")
    open fun updateCorridor(corridor: Corridor, newCapacity: Int, newPenalty: Double, newWard: String, newCategory: String) : Corridor? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            DELETE {
                bedreflyt:${corridor.hospital.hospitalCode}_${corridor.treatmentWard.wardName}_Corridor${corridor.roomNumber} a bedreflyt:Corridor ;
                    bedreflyt:hasMonitoringStatus ?category ;
                    bedreflyt:isAssignWard ?ward ;
                    bedreflyt:penalty ?penalty ;
                    bedreflyt:hasCapacityNrBeds ?capacity .
            }
            INSERT {
                bedreflyt:${corridor.hospital.hospitalCode}_${corridor.treatmentWard.wardName}_Corridor${corridor.roomNumber} a bedreflyt:Corridor ;
                    bedreflyt:hasMonitoringStatus bedreflyt:$newCategory ;
                    bedreflyt:isAssignWard bedreflyt:$newWard ;
                    bedreflyt:penalty "$newPenalty"^^xsd:double ;
                    bedreflyt:hasCapacityNrBeds $newCapacity .
            }
            WHERE {
                bedreflyt:${corridor.hospital.hospitalCode}_${corridor.treatmentWard.wardName}_Corridor${corridor.roomNumber} a bedreflyt:Corridor ;
                    bedreflyt:hasMonitoringStatus ?category ;
                    bedreflyt:isAssignWard ?ward ;
                    bedreflyt:penalty ?penalty ;
                    bedreflyt:hasCapacityNrBeds ?capacity .
            }
        """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()

                val ward = wardService.getWardByNameAndHospital(newWard, corridor.hospital.hospitalCode) ?: return null
                val hospital = hospitalService.getHospitalByCode(corridor.hospital.hospitalCode) ?: return null
                val category = monitoringCategoryService.getCategoryByDescription(newCategory) ?: return null

                replConfig.regenerateSingleModel().invoke("offices")

                log.info("Office updated successfully: ${corridor.roomNumber} in ${corridor.treatmentWard.wardName} at ${corridor.hospital.hospitalCode}")
                return Corridor(
                    corridor.roomNumber,
                    newCapacity,
                    newPenalty,
                    ward,
                    hospital,
                    category
                )
            } catch (_: Exception) {
                log.error("Error updating office: ${corridor.roomNumber} in ${corridor.treatmentWard.wardName} at ${corridor.hospital.hospitalCode}")
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict(value = ["corridors"], allEntries = true)
    open fun deleteCorridor(corridor: Corridor): Boolean {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE WHERE {
                bedreflyt:${corridor.hospital.hospitalCode}_${corridor.treatmentWard.wardName}_Corridor${corridor.roomNumber} a bedreflyt:Corridor ;
                    bedreflyt:hasMonitoringStatus ?category ;
                    bedreflyt:isAssignWard ?ward ;
                    bedreflyt:penalty ?penalty ;
                    bedreflyt:hasCapacityNrBeds ?capacity .
            }
        """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("offices")
                log.info("Office deleted successfully: ${corridor.roomNumber} in ${corridor.treatmentWard.wardName} at ${corridor.hospital.hospitalCode}")

                return true
            } catch (_: Exception) {
                log.error("Error deleting office: ${corridor.roomNumber} in ${corridor.treatmentWard.wardName} at ${corridor.hospital.hospitalCode}")
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}