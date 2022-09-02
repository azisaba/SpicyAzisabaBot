package net.azisaba.spicyazisababot.util

import dev.kord.common.entity.CommandArgument
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.SubCommand
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.ModalParentInteractionBehavior
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.cache.data.AttachmentData
import dev.kord.core.entity.Message
import dev.kord.core.entity.interaction.Interaction
import dev.kord.core.entity.interaction.ModalSubmitInteraction
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.ModalBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mariadb.jdbc.Driver
import xyz.acrylicstyle.util.InvalidArgumentException
import java.net.URL
import java.sql.Connection
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KProperty

object Util {
    private val cache = mutableMapOf<String, Pair<Long, String>>()

    fun String.validateTable(): Boolean = "^[\u0041-\u005A\u0061-\u007A0-9_\\-.]+$".toRegex().matches(this)

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
        setProperty(
            "verifyServerCertificate",
            getEnvOrElse("MARIADB_VERIFY_SERVER_CERT", "true").toBoolean().toString()
        )
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

    private fun generateDatabaseURL() =
        "jdbc:mariadb://${getEnvOrThrow("MARIADB_HOST")}/${getEnvOrThrow("MARIADB_NAME")}"

    fun getConnection(): Connection =
        try {
            Driver().connect(generateDatabaseURL() + getProperties().toQuery(), getProperties())
        } catch (e: Exception) {
            throw InvalidArgumentException(
                "Failed to connect to database (Attempted to use url: '${generateDatabaseURL()}')",
                e
            )
        }

    fun Message.mentionsSelf(): Boolean = this.mentionedUserIds.contains(this.kord.selfId)

    fun InvalidArgumentException.toDiscord(): String {
        val errorComponent = "Invalid syntax: $message"
        val context = this.context ?: return errorComponent
        val prev = context.peekWithAmount(-min(context.index(), 15))
        var next = context.peekWithAmount(
            min(
                context.readableCharacters(),
                max(15, length)
            )
        )
        if (next.isEmpty()) {
            next = " ".repeat(length)
        }
        val left = next.substring(0, length)
        val right = next.substring(length, next.length)
        return "$errorComponent\n${prev}__${left}__$right"
    }

    fun Interaction.optAny(name: String): Any? =
        this.data
            .data
            .options
            .value
            ?.find { it.name == name }
            ?.value
            ?.value
            ?.value

    fun Interaction.optString(name: String) = optAny(name) as String?

    fun Interaction.optSnowflake(name: String) = optAny(name) as Snowflake?

    fun Interaction.optLong(name: String) = optAny(name) as Long?

    fun Interaction.optAttachments(): List<AttachmentData> =
        this.data
            .data
            .resolvedObjectsData
            .value
            ?.attachments
            ?.value
            ?.values
            ?.toList()
            ?: emptyList()

    fun Interaction.optSubCommands(groupName: String, subCommandName: String): SubCommand? =
        this.data
            .data
            .options
            .value
            ?.find { it.name == groupName }
            ?.subCommands
            ?.value
            ?.find { it.name == subCommandName }

    fun Optional<List<CommandArgument<*>>>.optAny(name: String) = value?.find { it.name == name }?.value
    fun Optional<List<CommandArgument<*>>>.optString(name: String) = optAny(name) as String?
    fun Optional<List<CommandArgument<*>>>.optBoolean(name: String) = optAny(name) as Boolean?
    fun Optional<List<CommandArgument<*>>>.optSnowflake(name: String) = optAny(name) as Snowflake?
    fun Optional<List<CommandArgument<*>>>.optLong(name: String) = optAny(name) as Long?

    suspend fun ModalParentInteractionBehavior.modal(title: String, modalBuilder: ModalBuilder.() -> Unit, action: suspend ModalSubmitInteraction.() -> Unit) {
        val uuid = UUID.randomUUID().toString()
        var jobReference by AtomicReference<Job>()
        var cancelJobReference by AtomicReference<Job>()
        jobReference = kord.on<ModalSubmitInteractionCreateEvent> {
            if (interaction.modalId == uuid) {
                jobReference?.cancel()
                cancelJobReference?.cancel()
                action(interaction)
            }
        }
        withContext(kord.coroutineContext) {
            cancelJobReference = launch {
                delay(1000 * 60 * 15) // 15 minutes
                if (jobReference?.isCancelled == false) {
                    jobReference?.cancel()
                }
            }
            modal(title, uuid, modalBuilder)
        }
    }

    operator fun <V> AtomicReference<V>.getValue(thisRef: AtomicReference<V>?, property: KProperty<*>): V? =
        get()

    operator fun <V> AtomicReference<V>.setValue(thisRef: AtomicReference<V>?, property: KProperty<*>, value: V?) =
        set(value)
}
