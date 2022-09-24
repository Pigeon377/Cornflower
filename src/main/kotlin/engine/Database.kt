package engine

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 *
 * the only place, program can interactive with DB
 * SQL Sentence will always be executed in this object
 * Query result will always be generated in this object and return to upper function
 *
 * Example:
 *
 * ```kotlin
 *
 * Database.connect("jdbc:sqlite:test.db")
 * val r = Database.execute("CREATE TABLE wry(name varchar(255))")
 * // r = true / false
 * val res = Database.executeQuery("SELECT * FROM wry")
 * // res = ResultSet(?)
 *
 * ```
 *
 * */
object Database {

    // don't change it!
    private lateinit var connection: Connection

    fun connect(
        connectUrl: String,
        user: String? = null,
        password: String? = null,
        driver: String? = null,
        openTransaction: Boolean = true
    ) {

        if (driver != null && driver.isNotBlank()) {
            Class.forName(driver)
        }

        connection = DriverManager.getConnection(connectUrl, user, password)
        if (openTransaction) {
            connection.autoCommit = false
        }
    }

    fun closeConnection() {
        connection.close()
    }

    private fun statement(): Statement {
        return connection.createStatement()
    }

    internal fun execute(sql: String): Boolean {
        return statement().execute(sql)
    }

    internal fun executeQuery(sql: String): ResultSet? {
        return statement().executeQuery(sql)
    }

    internal fun endTransaction() {
        // open transaction
        if (!connection.autoCommit) {
            try {
                connection.commit()
            } catch (e: SQLException) {
                e.printStackTrace()
                connection.rollback()
            }
        }
    }
}