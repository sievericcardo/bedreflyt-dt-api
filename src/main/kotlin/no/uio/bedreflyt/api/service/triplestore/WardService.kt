package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Hospital
import no.uio.bedreflyt.api.model.triplestore.Ward
import no.uio.bedreflyt.api.types.WardRequest
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class WardService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val hospitalService: HospitalService,
    private val floorService: FloorService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    @CacheEvict(value = ["wards"], allEntries = true)
    @CachePut("wards", key = "#request.wardName + '_' + #hospital.hospitalCode")
    open fun createWard (request: WardRequest, hospital: Hospital) : Ward? {
        lock.writeLock().lock()
        try {
            val wardCodeLine = request.wardCode?.let { "bedreflyt:wardCode $it ;" } ?: ""
            val name = request.wardName.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            INSERT DATA {
                bedreflyt:$name a bedreflyt:Ward ;
                    brick:hasHospital bedreflyt:${hospital.hospitalName} ;
                    brick:hasFloor bedreflyt:Floor${request.wardFloorNumber} ;
                    $wardCodeLine
                    bedreflyt:wardName "${request.wardName}" ;
                    bedreflyt:capacityThreshold "${request.capacityThreshold}"^^xsd:double .
            }
        """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                val ward = Ward(
                    request.wardName,
                    request.wardCode,
                    request.capacityThreshold,
                    hospitalService.getHospitalByCode(hospital.hospitalCode)!!,
                    floorService.getFloorByNumber(request.wardFloorNumber)!!
                )
                replConfig.regenerateSingleModel().invoke("wards")

                return ward
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable(value = ["wards"], key = "'allWards'")
    open fun getAllWards() : List<Ward>? {
        lock.readLock().lock()
        try {
            val wards = mutableListOf<Ward>()

            val query =
                """
                    SELECT DISTINCT ?wardName ?wardCode ?capacityThreshold ?hospitalCode ?floorNumber WHERE {
                        ?ward a prog:Ward ;
                            prog:Ward_wardName ?wardName ;
                            prog:Ward_wardCode ?wardCode ;
                            prog:Ward_capacityThreshold ?capacityThreshold ;
                            prog:Ward_wardHospital ?wardHospital ;
                            prog:Ward_wardFloor ?wardFloor .
                        ?wardHospital a prog:Hospital ;
                            prog:Hospital_hospitalCode ?hospitalCode .
                        ?wardFloor a prog:Floor ;
                            prog:Floor_floorNumber ?floorNumber .
                }"""

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if(!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result: QuerySolution = resultSet.next()
                val wardName = result.get("wardName").toString()
                val wardCode = result.get("wardCode")?.toString()
                val capacityThreshold = result.get("capacityThreshold").asLiteral().toString().split("^^")[0].toDouble()
                val hospitalName = result.get("hospitalCode").toString()
                val floorNumber = result.get("floorNumber").asLiteral().toString().split("^^")[0].toInt()

                val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
                val floor = floorService.getFloorByNumber(floorNumber) ?: continue

                wards.add(Ward(wardName, wardCode, capacityThreshold, hospital, floor))
            }

            return wards
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("wards", key = "'allWardsExcept_' + #wardName + '_' + #hospitalCode")
    open fun getAllWardsExcept(wardName: String, hospitalCode: String) : List<Ward>? {
        lock.readLock().lock()
        try {
            val wards = mutableListOf<Ward>()

            val query = """
            SELECT DISTINCT ?wardName ?wardCode ?capacityThreshold ?hospitalCode ?floorNumber WHERE {
                ?ward a prog:Ward ;
                    prog:Ward_wardName ?wardName ;
                    prog:Ward_wardCode ?wardCode ;
                    prog:Ward_capacityThreshold ?capacityThreshold ;
                    prog:Ward_wardHospital ?wardHospital ;
                    prog:Ward_wardFloor ?wardFloor .
                ?wardHospital a prog:Hospital ;
                    prog:Hospital_hospitalCode ?hospitalCode .
                ?wardFloor a prog:Floor ;
                    prog:Floor_floorNumber ?floorNumber .
                FILTER (?hospitalCode != "$hospitalCode" && ?wardName != "$wardName")
            }
        """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result: QuerySolution = resultSet.next()
                val wardNameNew = result.get("wardName").toString()
                val wardCode = result.get("wardCode")?.toString()
                val capacityThreshold = result.get("capacityThreshold").asLiteral().toString().split("^^")[0].toDouble()
                val hospitalName = result.get("hospitalCode").toString()
                val floorNumber = result.get("floorNumber").asLiteral().toString().split("^^")[0].toInt()

                val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
                val floor = floorService.getFloorByNumber(floorNumber) ?: continue

                wards.add(
                    Ward(
                        wardNameNew,
                        wardCode,
                        capacityThreshold,
                        hospital,
                        floor
                    )
                )
            }

            return wards
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("wards", key = "#wardName + '_' + #wardHospital")
    open fun getWardByNameAndHospital (wardName: String, wardHospital: String) : Ward? {
        lock.readLock().lock()
        try {
            val query = """
            SELECT DISTINCT ?wardName ?wardCode ?capacityThreshold ?hospitalCode ?floorNumber WHERE {
                ?ward a prog:Ward ;
                    prog:Ward_wardName "$wardName" ;
                    prog:Ward_wardCode ?wardCode ;
                    prog:Ward_capacityThreshold ?capacityThreshold ;
                    prog:Ward_wardHospital ?wardHospital ;
                    prog:Ward_wardFloor ?wardFloor .
                ?wardHospital a prog:Hospital ;
                    prog:Hospital_hospitalCode "$wardHospital" .
                ?wardFloor a prog:Floor ;
                    prog:Floor_floorNumber ?floorNumber .
            }
        """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            val result: QuerySolution = resultSet.next()
            val wardCode = result.get("wardCode")?.toString()
            val capacityThreshold = result.get("capacityThreshold").asLiteral().toString().split("^^")[0].toDouble()
            val floorNumber = result.get("floorNumber").toString().split("^^")[0].toInt()

            val hospital = hospitalService.getHospitalByCode(wardHospital) ?: return null
            val floor = floorService.getFloorByNumber(floorNumber) ?: return null
            val ward = Ward(
                wardName,
                wardCode,
                capacityThreshold,
                hospital,
                floor
            )

            return ward
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["wards"], allEntries = true)
    @CachePut("wards", key = "#ward.wardName + '_' + #ward.wardHospital.hospitalCode")
    open fun updateWard (ward: Ward,  newFloorNumber: Int) : Ward? {
        lock.writeLock().lock()
        try {
            val name = ward.wardName.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE {
                bedreflyt:$name a bedreflyt:Ward ;
                    bedreflyt:wardName "${ward.wardName}" ;
                    brick:hasHospital bedreflyt:${ward.wardHospital.hospitalCode} ;
                    brick:hasFloor bedreflyt:Floor${ward.wardFloor.floorNumber} .
            }
            INSERT {
                bedreflyt:$name a bedreflyt:Ward ;
                    bedreflyt:wardName "${ward.wardName}" ;
                    brick:hasHospital bedreflyt:${ward.wardHospital.hospitalCode} ;
                    brick:hasFloor bedreflyt:Floor${newFloorNumber} .
            }
            WHERE {
                bedreflyt:$name a bedreflyt:Ward ;
                    bedreflyt:wardName "${ward.wardName}" ;
                    brick:hasHospital bedreflyt:${ward.wardHospital.hospitalCode} ;
                    brick:hasFloor bedreflyt:Floor${ward.wardFloor.floorNumber} .
            }
        """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("wards")
                val ward = Ward(
                    ward.wardName,
                    ward.wardCode,
                    ward.capacityThreshold,
                    hospitalService.getHospitalByCode(ward.wardHospital.hospitalCode)!!,
                    floorService.getFloorByNumber(newFloorNumber)!!
                )

                return ward
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict(value = ["wards"], allEntries = true)
    open fun deleteWard (ward: Ward) : Boolean {
        lock.writeLock().lock()
        try {
            val name = ward.wardName.trim().replace(" ", "")

            val query = """
                PREFIX bedreflyt: <$prefix>
                PREFIX brick: <https://brickschema.org/schema/Brick#>
                
                DELETE {
                    bedreflyt:$name ?p ?o .
                }
                WHERE {
                    bedreflyt:$name ?p ?o .
                }
            """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()

                replConfig.regenerateSingleModel().invoke("wards")
                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}