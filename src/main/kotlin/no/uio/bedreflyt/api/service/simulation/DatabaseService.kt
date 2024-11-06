package no.uio.bedreflyt.api.service

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.stereotype.Service

@Service
class DatabaseService {

    private fun getJdbcTemplate(dbPath: String): JdbcTemplate {
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName("org.sqlite.JDBC")
        dataSource.url = "jdbc:sqlite:$dbPath"
        return JdbcTemplate(dataSource)
    }

    fun createPatientTable(dbPath: String) {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val createPatientTable = """
            CREATE TABLE IF NOT EXISTS patient (
                patientId TEXT PRIMARY KEY,
                gender TEXT NOT NULL,
                genderModl INTEGER NOT NULL
            )
        """

        val createPatientStatusTable = """
            CREATE TABLE IF NOT EXISTS patientStatus (
                patientId TEXT PRIMARY KEY,
                infectious BOOLEAN NOT NULL,
                roomNr INTEGER NOT NULL
            )
        """

        val createScenarioTable = """
            CREATE TABLE IF NOT EXISTS scenario (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch INTEGER NOT NULL,
                patientId TEXT NOT NULL,
                treatmentName TEXT NOT NULL
            )
        """

        jdbcTemplate.execute(createPatientTable)
        jdbcTemplate.execute(createPatientStatusTable)
        jdbcTemplate.execute(createScenarioTable)
    }

    fun createRoomTables(dbPath: String) {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val createRoomTable = """
            CREATE TABLE IF NOT EXISTS roomCategory (
                catId LONG PRIMARY KEY,
                roomDescr TEXT NOT NULL,
                catIdModl LONG NOT NULL
            )
        """
        val createRoomDistributionTable = """
            CREATE TABLE IF NOT EXISTS roomDistrib (
                roomNr INTEGER PRIMARY KEY,
                roomNrMod INTEGER NOT NULL,
                bedCategory INTEGER NOT NULL,
                capacity INTEGER NOT NULL,
                bathroom BOOLEAN NOT NULL
            )
        """
        jdbcTemplate.execute(createRoomTable)
        jdbcTemplate.execute(createRoomDistributionTable)
    }

    fun insertPatient(dbPath: String, patientId: String, gender: String) {
        if (getPatientById(dbPath, patientId) != null) {
            return
        }
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val genderModl = gender.equals("Male")
        val sql = "INSERT INTO patient (patientId, gender, genderModl) VALUES (?, ?, ?)"
        jdbcTemplate.update(sql, patientId, gender, genderModl)
    }

    fun insertPatientStatus(dbPath: String, patientId: String, infectious: Boolean, roomNr: Int) {
        if (getPatientStatusById(dbPath, patientId) != null) {
            return
        }
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "INSERT INTO patientStatus (patientId, infectious, roomNr) VALUES (?, ?, ?)"
        jdbcTemplate.update(sql, patientId, infectious, roomNr)
    }

    fun insertRoom(dbPath: String, id: Long?, description: String) {
        if (getRoomById(dbPath, id!!) != null) {
            return
        }
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "INSERT INTO roomCategory (catId, roomDescr, catIdModl) VALUES (?, ?, ?)"
        jdbcTemplate.update(sql, id, description, id)
    }

    fun insertRoomDistribution(dbPath: String, roomNumber: Long, roomNumberModel: Long, roomCategory: Long, capacity: Int, bathroom: Boolean) {
        if (getRoomDistributionById(dbPath, roomNumber) != null) {
            return
        }
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "INSERT INTO roomDistrib (roomNr, roomNrMod, bedCategory, capacity, bathroom) VALUES (?, ?, ?, ?, ?)"
        jdbcTemplate.update(sql, roomNumber, roomNumberModel, roomCategory, capacity, bathroom)
    }

    fun insertScenario(dbPath: String, batch: Int, patientId: String, treatmentName: String) {
        if (getScenarioByData(dbPath, batch, patientId, treatmentName) != null) {
            return
        }
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "INSERT INTO scenario (batch, patientId, treatmentName) VALUES (?, ?, ?)"
        jdbcTemplate.update(sql, batch, patientId, treatmentName)
    }

    fun getRooms(dbPath: String): List<Map<String, Any>> {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM roomCategory"
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (e: EmptyResultDataAccessException) {
            emptyList()
        }
    }

    fun getRoomById(dbPath: String, id: Long): Map<String, Any>? {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM roomCategory WHERE catId = ?"
        return try {
            jdbcTemplate.queryForMap(sql, id)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getRoomDistributions(dbPath: String): List<Map<String, Any>> {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM roomDistrib"
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (e: EmptyResultDataAccessException) {
            emptyList()
        }
    }

    fun getRoomDistributionById(dbPath: String, id: Long): Map<String, Any>? {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM roomDistrib WHERE roomNr = ?"
        return try {
            jdbcTemplate.queryForMap(sql, id)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getPatients(dbPath: String): List<Map<String, Any>> {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM patient"
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (e: EmptyResultDataAccessException) {
            emptyList()
        }
    }

    fun getPatientById(dbPath: String, id: String): Map<String, Any>? {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM patient WHERE patientId = ?"
        return try {
            jdbcTemplate.queryForMap(sql, id)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getPatientStatuses(dbPath: String): List<Map<String, Any>> {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM patientStatus"
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (e: EmptyResultDataAccessException) {
            emptyList()
        }
    }

    fun getPatientStatusById(dbPath: String, id: String): Map<String, Any>? {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM patientStatus WHERE patientId = ?"
        return try {
            jdbcTemplate.queryForMap(sql, id)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getScenarios(dbPath: String): List<Map<String, Any>> {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM scenario"
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (e: EmptyResultDataAccessException) {
            emptyList()
        }
    }

    fun getScenarioByData(dbPath: String, batch: Int, patientId: String, treatmentName: String): Map<String, Any>? {
        val jdbcTemplate = getJdbcTemplate(dbPath)
        val sql = "SELECT * FROM scenario WHERE batch = ? AND patientId = ? AND treatmentName = ?"
        return try {
            jdbcTemplate.queryForMap(sql, batch, patientId, treatmentName)
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }
}