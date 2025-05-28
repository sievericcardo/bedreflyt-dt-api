package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Office
import no.uio.bedreflyt.api.types.OfficeRequest
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class OfficeService (
    private val replConfig: REPLConfig,
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
    private val log: Logger = LoggerFactory.getLogger(OfficeService::class.java.name)
    private val lock = ReentrantReadWriteLock()

    open fun createOffice(officeRequest: OfficeRequest): Office? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            INSERT DATA {
                bedreflyt:${officeRequest.hospital}_${officeRequest.ward}_Office${officeRequest.roomNumber} a bedreflyt:Office ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${officeRequest.categoryDescription} ;
                    bedreflyt:isAssignWard bedreflyt:${officeRequest.ward} ;
                    bedreflyt:available "${officeRequest.available}"^^xsd:boolean ;
                    bedreflyt:hasCapacityNrBeds ${officeRequest.capacity} ;
                    bedreflyt:hasRoomNr ${officeRequest.roomNumber} ;
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()

                val ward = wardService.getWardByNameAndHospital(officeRequest.ward, officeRequest.hospital)!!
                val hospital = hospitalService.getHospitalByCode(officeRequest.hospital)!!
                val category = monitoringCategoryService.getCategoryByDescription(officeRequest.categoryDescription)!!

                replConfig.regenerateSingleModel().invoke("offices")

                log.info("Office created successfully: ${officeRequest.roomNumber} in ${officeRequest.ward} at ${officeRequest.hospital}")
                val office = Office(
                    officeRequest.roomNumber,
                    officeRequest.capacity,
                    officeRequest.available,
                    ward,
                    hospital,
                    category
                )

                cacheManager.getCache("offices")?.put(officeRequest.roomNumber, office)
                return office
            } catch (_: Exception) {
                log.error("Error creating office: ${officeRequest.roomNumber} in ${officeRequest.ward} at ${officeRequest.hospital}")
                return null
            }
        } catch (e: Exception) {
            log.error("Error creating office: $e")
            return null
        } finally {
            lock.writeLock().unlock()
        }
    }

    open fun getAllOffices() : List<Office>? {
        lock.readLock().lock()
        try {
            val offices = mutableListOf<Office>()

            val query = """
            SELECT ?roomNumber ?capacity ?available ?wardName ?hospitalCode ?categoryDescription WHERE {
                ?office a prog:Office ;
                    prog:Office_roomNumber ?roomNumber ;
                    prog:Office_capacity ?capacity ;
                    prog:Office_available ?available ;
                    prog:Office_officeWard ?wardObj ;
                    prog:Office_hospital ?hospitalObj ;
                    prog:Office_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName ?wardName .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode ?hospitalCode .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }
        """.trimIndent()

            val resultSet = repl.interpreter!!.query(query) ?: return null

            while (resultSet.hasNext()) {
                val result = resultSet.next()
                val roomNumber = result.get("roomNumber").toString().split("^^")[0].toInt()
                val capacity = result.get("capacity").asLiteral().toString().split("^^")[0].toInt()
                val available = result.get("available").asLiteral().toString().split("^^")[0].toBoolean()
                val wardName = result.get("wardName").toString()
                val hospitalCode = result.get("hospitalCode").toString()
                val categoryDescription = result.get("categoryDescription").toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: continue
                val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: continue
                val category = monitoringCategoryService.getCategoryByDescription(categoryDescription) ?: continue

                offices.add(Office(roomNumber, capacity, available, ward, hospital, category))
            }

            cacheManager.getCache("offices")?.put("allOffices", offices)
            return offices
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun getOfficeByRoonNumberWardHospital (roomNumber: Int, wardName: String, hospitalCode: String): Office? {
        lock.readLock().lock()
        try {
            val query = """
            SELECT ?capacity ?available ?categoryDescription WHERE {
                ?office a prog:Office ;
                    prog:Office_roomNumber $roomNumber ;
                    prog:Office_capacity ?capacity ;
                    prog:Office_available ?available ;
                    prog:Office_officeWard ?wardObj ;
                    prog:Office_hospital ?hospitalObj ;
                    prog:Office_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName "$wardName" .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode "$hospitalCode" .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }
        """.trimIndent()

            val resultSet = repl.interpreter!!.query(query) ?: return null

            if (!resultSet.hasNext()) {
                return null
            }

            val result = resultSet.next()
            val capacity = result.get("capacity").asLiteral().toString().split("^^")[0].toInt()
            val available = result.get("available").asLiteral().toString().split("^^")[0].toBoolean()
            val categoryDescription = result.get("categoryDescription").toString()

            val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return null
            val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return null
            val category = monitoringCategoryService.getCategoryByDescription(categoryDescription) ?: return null

            return Office(roomNumber, capacity, available, ward, hospital, category)
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun getOfficeByWardHospital (wardName: String, hospitalCode: String): List<Office>? {
        lock.readLock().lock()
        try {
            val offices = mutableListOf<Office>()

            val query = """
            SELECT ?roomNumber ?capacity ?available ?categoryDescription WHERE {
                ?office a prog:Office ;
                    prog:Office_officeWard ?wardObj ;
                    prog:Office_hospital ?hospitalObj ;
                    prog:Office_roomNumber ?roomNumber ;
                    prog:Office_capacity ?capacity ;
                    prog:Office_available ?available ;
                    prog:Office_monitoringCategory ?categoryObj .
                ?wardObj a prog:Ward ;
                    prog:Ward_wardName "$wardName" .
                ?hospitalObj a prog:Hospital ;
                    prog:Hospital_hospitalCode "$hospitalCode" .
                ?categoryObj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?categoryDescription .
            }
        """.trimIndent()

            val resultSet = repl.interpreter!!.query(query) ?: return null

            while (resultSet.hasNext()) {
                val result = resultSet.next()
                val roomNumber = result.get("roomNumber").toString().split("^^")[0].toInt()
                val capacity = result.get("capacity").asLiteral().toString().split("^^")[0].toInt()
                val available = result.get("available").asLiteral().toString().split("^^")[0].toBoolean()
                val categoryDescription = result.get("categoryDescription").toString()

                val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: continue
                val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: continue
                val category = monitoringCategoryService.getCategoryByDescription(categoryDescription) ?: continue

                offices.add(Office(roomNumber, capacity, available, ward, hospital, category))
            }

            cacheManager.getCache("offices")?.put("$wardName-$hospitalCode", offices)
            return offices
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun updateOffice(office: Office, newCapacity: Int, newAvailable: Boolean, newWard: String, newCategory: String) : Office? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            DELETE {
                bedreflyt:${office.hospital.hospitalCode}_${office.treatmentWard.wardName}_Office${office.roomNumber} a bedreflyt:Office ;
                    bedreflyt:hasMonitoringStatus ?oldCategory ;
                    bedreflyt:isAssignWard ?oldWard ;
                    bedreflyt:available ?oldAvailable ;
                    bedreflyt:hasCapacityNrBeds ?oldCapacity ;
            }
            INSERT {
                bedreflyt:${office.hospital.hospitalCode}_${office.treatmentWard.wardName}_Office${office.roomNumber} a bedreflyt:Office ;
                    bedreflyt:hasMonitoringStatus bedreflyt:$newCategory ;
                    bedreflyt:isAssignWard bedreflyt:$newWard ;
                    bedreflyt:available "$newAvailable"^^xsd:boolean ;
                    bedreflyt:hasCapacityNrBeds $newCapacity ;
            }
            WHERE {
                bedreflyt:${office.hospital.hospitalCode}_${office.treatmentWard.wardName}_Office${office.roomNumber} a bedreflyt:Office .;
                    bedreflyt:hasMonitoringStatus ?oldCategory ;
                    bedreflyt:isAssignWard ?oldWard ;
                    bedreflyt:available ?oldAvailable ;
                    bedreflyt:hasCapacityNrBeds ?oldCapacity .
                    bedreflyt:hasRoomNr ${office.roomNumber} .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()

                val ward = wardService.getWardByNameAndHospital(newWard, office.hospital.hospitalCode) ?: return null
                val hospital = hospitalService.getHospitalByCode(office.hospital.hospitalCode) ?: return null
                val category = monitoringCategoryService.getCategoryByDescription(newCategory) ?: return null

                replConfig.regenerateSingleModel().invoke("offices")
                log.info("Office updated successfully: ${office.roomNumber} in ${newWard} at ${office.hospital.hospitalCode}")
                val updatedOffice = Office(
                    office.roomNumber,
                    newCapacity,
                    newAvailable,
                    ward,
                    hospital,
                    category
                )

                cacheManager.getCache("offices")
                    ?.put("${office.roomNumber}-${office.hospital.hospitalCode}-${office.treatmentWard.wardName}", updatedOffice)
                return updatedOffice
            } catch (e: Exception) {
                log.error("Error updating office: $e")
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }


    open fun deleteOffice(office: Office): Boolean {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE WHERE {
                bedreflyt:${office.hospital.hospitalCode}_${office.treatmentWard.wardName}_Office${office.roomNumber} a bedreflyt:Office ;
                    bedreflyt:hasMonitoringStatus ?category ;
                    bedreflyt:isAssignWard ?ward ;
                    bedreflyt:available ?available ;
                    bedreflyt:hasCapacityNrBeds ?capacity .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("offices")
                log.info("Office deleted successfully: ${office.roomNumber} in ${office.treatmentWard.wardName} at ${office.hospital.hospitalCode}")

                cacheManager.getCache("offices")?.evict("${office.roomNumber}-${office.hospital.hospitalCode}-${office.treatmentWard.wardName}")
                return true
            } catch (e: Exception) {
                log.error("Error deleting office: $e")
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}