package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.AllowedMentionType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.azisaba.spicyazisababot.config.BotConfig
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.Util.optSubcommand
import java.io.File
import java.lang.IllegalArgumentException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

class RemindCommand(kord: Kord) : CommandHandler {
    private val formats = listOf(
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
        SimpleDateFormat("yyyy/MM/dd HH:mm"),
        SimpleDateFormat("yyyy/MM/dd"),
        SimpleDateFormat("HH:mm:ss"),
        SimpleDateFormat("HH:mm"),
        SimpleDateFormat("MM/dd"),
        SimpleDateFormat("MM/dd HH:mm"),
        SimpleDateFormat("MM/dd HH:mm:ss"),
    ).onEach { it.timeZone = TimeZone.getTimeZone(BotConfig.config.remindTimezone) }
    val reminds = mutableListOf<RemindData>()
    private val client = HttpClient(CIO)

    init {
        // load reminds
        try {
            val text = File("reminds.json").readText()
            reminds.addAll(Json.decodeFromString<List<RemindData>>(text))
        } catch (ignored: Exception) {}

        Timer(true).scheduleAtFixedRate(1000, 1000) {
            ArrayList(reminds).forEach { remindData ->
                if (System.currentTimeMillis() < remindData.at) return@forEach
                val loc = remindData.messageLocation
                reminds.remove(remindData)
                if (remindData.every != null) {
                    reminds.add(remindData.copy(at = remindData.at + remindData.every))
                }
                saveReminds()
                kord.launch {
                    kord.rest.channel.createMessage(loc.channelId) {
                        content = "<@${remindData.userId}>"
                        messageReference = loc.messageId
                        allowedMentions {
                            repliedUser = true
                            add(AllowedMentionType.EveryoneMentions)
                            add(AllowedMentionType.RoleMentions)
                            add(AllowedMentionType.UserMentions)
                        }
                        embed {
                            description = if (remindData.note != null) {
                                "${remindData.note}"
                            } else {
                                ""
                            }
                            description += "\n[メッセージリンク](https://discord.com/channels/${loc.guildId}/${loc.channelId}/${loc.messageId})"
                        }
                    }
                }
            }
        }
    }

    fun saveReminds() {
        try {
            val text = Json.encodeToString<MutableList<RemindData>>(reminds)
            File("reminds.json").writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun resolveDateTime(userId: Snowflake, text: String?): Long {
        if (text == null) return System.currentTimeMillis()
        if (BotSecretConfig.config.openAIApiKey.isBlank()) {
            return try {
                System.currentTimeMillis() + Util.processTime(text)
            } catch (_: IllegalArgumentException) {
                var newText = text
                var loopAgain: Boolean
                var value = -1L
                do {
                    loopAgain = false
                    formats.forEachIndexed { index, format ->
                        if (value != -1L) return@forEachIndexed
                        value = try {
                            val date = format.parse(newText)
                            val currentTime = System.currentTimeMillis() % 86400000
                            when (index) {
                                3 -> date.time + currentTime // add time
                                4, 5 -> date.time + System.currentTimeMillis() - currentTime // add date
                                6, 7, 8 -> {
                                    // Add "<year>/" for prefix and loop again
                                    newText = LocalDateTime.now(ZoneId.of(BotConfig.config.remindTimezone)).year.toString() + "/" + newText
                                    loopAgain = true
                                    -1L
                                }

                                else -> date.time
                            }
                        } catch (_: ParseException) {
                            -1L
                        }
                    }
                } while (loopAgain)
                value
            }
        }
        val rawResponse = client.post("https://api.openai.com/v1/chat/completions") {
            header("Content-Type", "application/json")
            header("Authorization", "Bearer ${BotSecretConfig.config.openAIApiKey}")
            setBody(Json.encodeToString(PostBody(
                "gpt-4",
                null,
                0.0,
                listOf(
                    ContentWithRole(
                        "system",
                        "Please extract the year, month, day, hour, minute, and second from user input." +
                                "Output in JSON format and each value can be omitted." +
                                "Example value is as follows: `{\"year\":2023,\"month\":12,\"day\":31,\"hour\":23,\"minute\":59,\"second\":0}`."
                    ),
                    ContentWithRole("user", text),
                ),
                false,
                userId.toString(),
            )))
        }.bodyAsText()
        val response = Json.decodeFromString<CompletionResult>(rawResponse)
        val now = LocalDateTime.now()
        val dateTime = Json.decodeFromString<DateTime>(response.choices[0].message.content)
        return DateTime(
            dateTime.year ?: now.year,
            dateTime.month ?: now.monthValue,
            dateTime.day ?: now.dayOfMonth,
            dateTime.hour ?: now.hour,
            dateTime.minute ?: now.minute,
            dateTime.second ?: now.second,
        ).toLong()
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferPublicResponse()
        try {
            interaction.optSubcommand("set")?.apply {
                val atUnparsed = optString("at")
                val every = optString("every")?.let { Util.processTime(it) }
                val note = optString("note")
                if (atUnparsed == null && every == null) {
                    defer.respond { content = "`at`もしくは`every`のどちらかを指定する必要があります。" }
                    return
                }
                val at = resolveDateTime(interaction.user.id, atUnparsed)
                if (at < 0) {
                    defer.respond { content = "`at`の値が無効です: `$atUnparsed`" }
                    return
                }
                val response = defer.respond { content = ":timer: ${formats[1].format(at)}" }
                reminds += RemindData(
                    MessageLocation(
                        interaction.channel.getGuildOrNull()?.id?.toString() ?: "@me",
                        interaction.channelId,
                        response.message.id,
                    ),
                    interaction.user.id,
                    at,
                    every,
                    note,
                )
                saveReminds()
            }
            interaction.optSubcommand("list")?.apply {
                var content = ""
                reminds.forEach { remindData ->
                    val loc = remindData.messageLocation
                    val every = remindData.every?.let { " (every ${Util.unProcessTime(it)})" } ?: ""
                    val note = remindData.note?.let { " `$it`" } ?: ""
                    content += "${formats[1].format(remindData.at)}$every$note [↗](https://discord.com/channels/${loc.guildId}/${loc.channelId}/${loc.messageId})\n"
                }
                defer.respond {
                    if (content.isBlank()) {
                        this.content = "リマインドがありません。"
                    } else {
                        embed {
                            description = content
                        }
                    }
                }
            }
        } catch (e: Exception) {
            defer.respond { content = "エラーが発生しました: `${e.message}`" }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("remind", "Remind") {
            subCommand("set", "新しいリマインドを登録します") {
                string("at", "<what>にリマインドします")
                string("every", "<what>時間ごとにリマインドします")
                string("note", "リマインド時に表示されるメッセージを指定します") {
                    maxLength = 255
                }
            }
            subCommand("list", "登録されているリマインドを表示します")
        }
    }
}

@Serializable
data class MessageLocation(
    val guildId: String,
    val channelId: Snowflake,
    val messageId: Snowflake,
)

@Serializable
data class RemindData(
    val messageLocation: MessageLocation,
    val userId: Snowflake,
    val at: Long,
    val every: Long?,
    val note: String?,
)

@Serializable
data class DateTime(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val second: Int? = null,
) {
    fun isEmpty() = year == null && month == null && day == null && hour == null && minute == null && second == null

    fun toLong() = LocalDateTime.of(
        year ?: 0,
        month ?: 1,
        day ?: 1,
        hour ?: 0,
        minute ?: 0,
        second ?: 0,
    ).atZone(ZoneId.of(BotConfig.config.remindTimezone)).toInstant().toEpochMilli()
}
