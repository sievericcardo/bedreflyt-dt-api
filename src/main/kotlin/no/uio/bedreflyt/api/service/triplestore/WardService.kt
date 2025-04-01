package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Ward
import no.uio.bedreflyt.api.types.WardRequest
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class WardService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val hospitalService: HospitalService,
    private val floorService: FloorService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("wards", key = "#request.wardName + _ + #request.wardHospitalName")
    fun createWard (request: WardRequest, hospitalName: String) : Boolean {
        val wardCodeLine = request.wardCode?.let { "bedreflyt:wardCode $it ;" } ?: ""
        val name = request.wardName.split(" ").joinToString("")

        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            INSERT DATA {
                bedreflyt:$name a bedreflyt:Ward ;
                    brick:hasHospital bedreflyt:$hospitalName ;
                    brick:hasFloor bedreflyt:Floor${request.wardFloorNumber} ;
                    $wardCodeLine
                    bedreflyt:wardName "${request.wardName}" .
            }
        """.trimIndent()

        val updateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Cacheable("wards")
    fun getAllWards() : List<Ward>? {
        val wards = mutableListOf<Ward>()

        val query =
            """
                SELECT DISTINCT ?wardName ?wardCode ?hospitalCode ?floorNumber WHERE {
                    ?ward a prog:Ward ;
                        prog:Ward_wardName ?wardName ;
                        prog:Ward_wardCode ?wardCode ;
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
            val hospitalName = result.get("hospitalCode").toString()
            val floorNumber = result.get("floorNumber").asLiteral().toString().split("^^")[0].toInt()

            val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
            val floor = floorService.getFloorByNumber(floorNumber) ?: continue

            wards.add(Ward(wardName, wardCode, hospital, floor))
        }

        return wards
    }

    @Cacheable
    fun getAllWardsExcept(wardName: String, hospitalCode: String) : List<Ward>? {
        val wards = mutableListOf<Ward>()

        val query = """
            SELECT DISTINCT ?wardName ?wardCode ?hospitalCode ?floorNumber WHERE {
                ?ward a prog:Ward ;
                    prog:Ward_wardName ?wardName ;
                    prog:Ward_wardCode ?wardCode ;
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
        if(!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val result: QuerySolution = resultSet.next()
            val wardNameNew = result.get("wardName").toString()
            val wardCode = result.get("wardCode")?.toString()
            val hospitalName = result.get("hospitalCode").toString()
            val floorNumber = result.get("floorNumber").asLiteral().toString().split("^^")[0].toInt()

            val hospital = hospitalService.getHospitalByCode(hospitalName) ?: continue
            val floor = floorService.getFloorByNumber(floorNumber) ?: continue

            wards.add(Ward(wardNameNew, wardCode, hospital, floor))
        }

        return wards
    }

    @Cacheable("wards", key = "#wardName + _ + #wardHospital")
    fun getWardByNameAndHospital (wardName: String, wardHospital: String) : Ward? {
        val query = """
            SELECT DISTINCT ?wardName ?wardCode ?hospitalCode ?floorNumber WHERE {
                ?ward a prog:Ward ;
                    prog:Ward_wardName "$wardName" ;
                    prog:Ward_wardCode ?wardCode ;
                    prog:Ward_wardHospital ?wardHospital ;
                    prog:Ward_wardFloor ?wardFloor .
                ?wardHospital a prog:Hospital ;
                    prog:Hospital_hospitalCode "$wardHospital" .
                ?wardFloor a prog:Floor ;
                    prog:Floor_floorNumber ?floorNumber .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        val result: QuerySolution = resultSet.next()
        val wardCode = result.get("wardCode")?.toString()
        val floorNumber = result.get("floorNumber").toString().split("^^")[0].toInt()

        val hospital = hospitalService.getHospitalByCode(wardHospital) ?: return null
        val floor = floorService.getFloorByNumber(floorNumber) ?: return null

        return Ward(wardName, wardCode, hospital, floor)
    }

    @CacheEvict("wards", key = "#ward.wardName + _ + #ward.wardHospital.hospitalCode")
    @CachePut("wards", key = "#ward.wardName + _ + #ward.wardHospital.hospitalCode")
    fun updateWard (ward: Ward,  newFloorNumber: Int) : Boolean {
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @CacheEvict("wards", key = "#ward.wardName + _ + #ward.wardHospital.hospitalCode")
    fun deleteWard (ward: Ward) : Boolean {
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
            WHERE {
                bedreflyt:$name a bedreflyt:Ward ;
                    bedreflyt:wardName "${ward.wardName}" ;
                    brick:hasHospital bedreflyt:${ward.wardHospital.hospitalCode}  ;
                    brick:hasFloor bedreflyt:Floor${ward.wardFloor.floorNumber} .
            }
        """.trimIndent()

        val updateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}