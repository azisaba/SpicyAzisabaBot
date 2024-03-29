package net.azisaba.spicyazisababot.config.secret

import kotlinx.serialization.Serializable
import org.mariadb.jdbc.Driver
import java.sql.Connection
import java.util.Properties

@Serializable
data class DatabaseConfig(
    val hostname: String = "localhost",
    val name: String = "spicyazisababot",
    val username: String = "spicyazisababot",
    val password: String = "spicyazisababot",
    val useSSL: Boolean = false,
    val verifyServerCertificate: Boolean = true,
) {
    private fun getProperties() = Properties().apply {
        setProperty("useSSL", useSSL.toString())
        setProperty("verifyServerCertificate", verifyServerCertificate.toString())
        setProperty("user", username)
        setProperty("password", password)
    }

    private fun Properties.toQuery(): String {
        val sb = StringBuilder("?")
        var notFirst = false
        this.forEach { o1: Any?, o2: Any? ->
            if (notFirst) sb.append('&')
            sb.append(o1).append("=").append(o2)
            notFirst = true
        }
        return sb.toString()
    }

    private fun generateDatabaseURL() =
        "jdbc:mariadb://$hostname/$name"

    fun getConnection(): Connection =
        try {
            Driver().connect(generateDatabaseURL() + getProperties().toQuery(), getProperties())
                ?: error("MariaDB driver did not accept url: " + generateDatabaseURL())
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to database (Attempted to use url: '${generateDatabaseURL()}')", e)
        }
}
