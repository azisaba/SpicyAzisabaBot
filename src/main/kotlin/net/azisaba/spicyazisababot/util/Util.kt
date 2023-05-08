package net.azisaba.spicyazisababot.util

import dev.kord.common.entity.CommandArgument
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.SubCommand
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.ModalParentInteractionBehavior
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.cache.data.AttachmentData
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.Interaction
import dev.kord.core.entity.interaction.ModalSubmitInteraction
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.ModalBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
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

    fun getConnection() = BotSecretConfig.config.database.getConnection()

    fun Interaction.optAny(name: String): Any? =
        when (this) {
            is ApplicationCommandInteraction ->
                this.data
                    .data
                    .options
                    .value
                    ?.find { it.name == name }
                    ?.value
                    ?.value
                    ?.value

            is ModalSubmitInteraction ->
                this.textInputs[name]?.value
                    ?: this.data.data.options.value?.find { it.name == name }?.value?.value?.value

            else -> null
        }

    fun Interaction.optString(name: String) = optAny(name)?.toString()

    fun Interaction.optSnowflake(name: String) = optString(name)?.toULong()?.let { Snowflake(it) }

    fun Interaction.optLong(name: String) = optDouble(name)?.toLong()

    fun Interaction.optDouble(name: String) = optString(name)?.toDouble()

    fun Interaction.optBoolean(name: String) = optString(name)?.toBoolean()

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

    fun Interaction.optSubcommand(name: String) =
        this.data
            .data
            .options
            .value
            ?.find { it.name == name }
            ?.values

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

    suspend fun ModalParentInteractionBehavior.modal(
        title: String,
        modalBuilder: ModalBuilder.() -> Unit,
        action: suspend ModalSubmitInteraction.() -> Unit
    ) {
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

    inline fun Timer.scheduleAtFixedRateBlocking(
        delay: Long,
        period: Long,
        crossinline action: suspend TimerTask.() -> Unit
    ): TimerTask {
        val task = object : TimerTask() {
            override fun run() {
                runBlocking {
                    action()
                }
            }
        }
        scheduleAtFixedRate(task, delay, period)
        return task
    }

    fun String.replaceWithMap(map: Map<String, *>): String {
        var s = this
        map.forEach { (key, value) ->
            s = s.replace(key, value.toString())
        }
        return s
    }

    fun createPostEventsFlow(url: String, body: String, headers: Map<String, String> = emptyMap()): Flow<EventData> =
        flow {
            val conn = (URL(url).openConnection() as HttpURLConnection).also {
                headers.forEach { (key, value) -> it.setRequestProperty(key, value) }
                it.setRequestProperty("Accept", "text/event-stream")
                it.doInput = true
                it.doOutput = true
            }

            conn.connect()

            conn.outputStream.write(body.toByteArray())

            if (conn.responseCode !in 200..399) {
                error("Request failed with ${conn.responseCode}: ${conn.errorStream.bufferedReader().readText()}")
            }

            val reader = conn.inputStream.bufferedReader()

            var event = EventData()

            while (true) {
                val line = reader.readLine() ?: break

                when {
                    line.startsWith("event:") -> event = event.copy(name = line.substring(6).trim())
                    line.startsWith("data:") -> event = event.copy(data = line.substring(5).trim())
                    line.isEmpty() -> {
                        emit(event)
                        event = EventData()
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    fun processTime(s: String): Long {
        var time = 0L
        var rawNumber = ""
        val reader = StringReader(s)
        while (!reader.isEOF()) {
            val c = reader.read(1).first()
            if (c.isDigit() || c == '.') {
                rawNumber += c
            } else {
                if (rawNumber.isEmpty()) {
                    throw IllegalArgumentException("Unexpected non-digit character: '$c' at index ${reader.index}")
                }
                // mo
                if (c == 'm' && !reader.isEOF() && reader.peek() == 'o') {
                    reader.skip(1)
                    time += month * rawNumber.toLong()
                    rawNumber = ""
                    continue
                }
                // y(ear), d(ay), h(our), m(inute), s(econd)
                time += when (c) {
                    'y' -> (year * rawNumber.toDouble()).toLong()
                    // mo is not here
                    'd' -> (day * rawNumber.toDouble()).toLong()
                    'h' -> (hour * rawNumber.toDouble()).toLong()
                    'm' -> (minute * rawNumber.toDouble()).toLong()
                    's' -> (second * rawNumber.toDouble()).toLong()
                    else -> throw IllegalArgumentException("Unexpected character: '$c' at index ${reader.index}")
                }
                rawNumber = ""
            }
        }
        return time
    }

    fun unProcessTime(l: Long): String {
        var time = l
        var text = ""
        if (time >= day) {
            val t = floor(time / day.toDouble()).toLong()
            text += "${t.toInt()}d"
            time -= t * day
        }
        if (time >= hour) {
            val t = floor(time / hour.toDouble()).toLong()
            text += "${t.toInt()}h"
            time -= t * hour
        }
        if (time >= minute) {
            val t = floor(time / minute.toDouble()).toLong()
            text += "${t.toInt()}m"
            time -= t * minute
        }
        if (time >= second) {
            val t = floor(time / second.toDouble()).toLong()
            text += "${t.toInt()}s"
            time -= t * second
        }
        return text
    }
}
