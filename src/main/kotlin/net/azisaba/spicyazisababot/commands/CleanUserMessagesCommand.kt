package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.azisaba.spicyazisababot.util.Util.optString
import java.io.ByteArrayInputStream
import java.io.File

object CleanUserMessagesCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction) = interaction.channel.getGuildOrNull() != null

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferEphemeralResponse()
        val channel = interaction.channel.getGuildOrNull()!!.getChannel(interaction.channelId)
        if (channel !is TopGuildMessageChannel && channel !is ThreadChannel) {
            error("unsupported channel type: ${channel.type}")
        }
        channel as GuildMessageChannel
        val user = interaction.optString("user")!!
        var count = 0
        val elements = mutableListOf<JsonElement>()
        try {
            channel.messages.collect { message ->
                if (message.author?.id?.toString() == user) {
                    message.delete("/clean-user-messages by ${interaction.user.id} - removing message from ${message.author?.id}")
                    elements.add(JsonObject(mapOf(
                        "id" to JsonPrimitive(message.id.value.toString()),
                        "content" to JsonPrimitive(message.content),
                        "attachments" to JsonArray(
                            message.attachments.map {
                                JsonObject(mapOf(
                                    "id" to JsonPrimitive(it.id.value.toString()),
                                    "url" to JsonPrimitive(it.url),
                                    "filename" to JsonPrimitive(it.filename),
                                    "size" to JsonPrimitive(it.size),
                                    "height" to JsonPrimitive(it.height),
                                    "width" to JsonPrimitive(it.width),
                                    "proxyUrl" to JsonPrimitive(it.proxyUrl),
                                ))
                            }
                        )
                    )))
                    count++
                }
            }
        } finally {
            val json = LinkGitHubCommand.json.encodeToString(elements)
            File("cleaned-messages.json").writeText(json)
            defer.respond {
                content = "${count}個のメッセージを削除しました。"
                ByteArrayInputStream(json.toByteArray()).use { input -> addFile("messages.json", input) }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("clean-user-messages", "ユーザーからのメッセージをすべて削除する") {
            string("user", "ユーザー") {
                required = true
                minLength = 10
            }

            dmPermission = false
        }
    }
}
