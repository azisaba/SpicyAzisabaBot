package net.azisaba.spicyAzisabaBot.util

import org.mariadb.jdbc.Driver
import java.net.URL
import java.sql.Connection
import java.util.Properties

object Util {
    private val cache = mutableMapOf<String, Pair<Long, String>>()

    fun executeCachedTextRequest(url: String, duration: Long): String {
        cache[url]?.let { (until, text) ->
            if (until < System.currentTimeMillis()) {
                cache.remove(url)
            } else {
                return text
            }
        }
        val text = URL(url).readText()
        val until = System.currentTimeMillis() + duration
        cache[url] = Pair(until, text)
        return text
    }

    fun getEnvOrThrow(name: String) = System.getenv(name) ?: error("Missing environment variable: $name")

    private fun getEnvOrElse(name: String, @Suppress("SameParameterValue") def: String) = System.getenv(name) ?: def

    private fun getProperties() = Properties().apply {
        setProperty("useSSL", getEnvOrElse("MARIADB_USE_SSL", "true").toBoolean().toString())
        setProperty("verifyServerCertificate", getEnvOrElse("MARIADB_VERIFY_SERVER_CERT", "true").toBoolean().toString())
        setProperty("user", getEnvOrThrow("MARIADB_USERNAME"))
        setProperty("password", getEnvOrThrow("MARIADB_PASSWORD"))
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

    private fun generateDatabaseURL() = "jdbc:mysql://${getEnvOrThrow("MARIADB_HOST")}/${getEnvOrThrow("MARIADB_NAME")}"

    fun getConnection(): Connection =
        Driver().connect(generateDatabaseURL() + getProperties().toQuery(), getProperties())
}
