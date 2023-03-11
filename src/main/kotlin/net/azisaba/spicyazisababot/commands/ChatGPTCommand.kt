package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.azisaba.spicyazisababot.util.Util.optAny
import net.azisaba.spicyazisababot.util.Util.optString

object ChatGPTCommand : CommandHandler {
    private val conversations = mutableMapOf<Snowflake, MutableList<ContentWithRole>>() // user id to messages
    private val client = HttpClient(CIO) {
        engine {
            this.requestTimeout = 1000 * 60 * 10
        }
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        if (interaction.invokedCommandName != "reply") {
            conversations[interaction.user.id]?.clear()
        }
        val text = interaction.optString("text")!!
        val temperature = (interaction.optAny("temperature") as? Number)?.toDouble() ?: 1.0
        val defer = interaction.deferPublicResponse()
        try {
            val response = client.post("https://api.openai.com/v1/chat/completions") {
                val thisMessage = ContentWithRole("user", text)
                conversations.computeIfAbsent(interaction.user.id) { mutableListOf() }.add(thisMessage)
                this.setBody(
                    LinkGitHubCommand.json.encodeToString(
                        PostBody(
                            "gpt-3.5-turbo",
                            2000,
                            temperature,
                            conversations[interaction.user.id] ?: error("conversation is null"),
                        )
                    )
                )
                header("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
                header("Content-Type", "application/json")
            }
            val body = LinkGitHubCommand.json.parseToJsonElement(response.bodyAsText())
            val choices = (body.jsonObject["choices"] ?: error("choices is not present in json: $body")).jsonArray
            val choice = LinkGitHubCommand.json.decodeFromJsonElement(ResponseChoice.serializer(), choices[0].jsonObject)
            conversations.computeIfAbsent(interaction.user.id) { mutableListOf() }.add(choice.message)
            val result = choice.message.content
            defer.respond {
                content = result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defer.respond {
                content = "エラーが発生しました。\n${e.message}"
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("reply", "ChatGPTを使って文章を生成します。") {
            string("text", "文章を生成するためのテキストを入力してください。") {
                required = true
            }
            number("temperature", "生成する文章の温度を指定します。") {
                required = false
                minValue = 0.0
                maxValue = 1.0
            }
        }
        builder.input("chatgpt", "ChatGPTを使って文章を生成します。") {
            string("text", "文章を生成するためのテキストを入力してください。") {
                required = true
            }
            number("temperature", "生成する文章の温度を指定します。") {
                required = false
                minValue = 0.0
                maxValue = 1.0
            }
        }
    }
}

@Serializable
internal data class PostBody(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Double,
    val messages: List<ContentWithRole>,
)

@Serializable
internal data class ContentWithRole(val role: String, val content: String)

@Serializable
internal data class ResponseChoice(
    val message: ContentWithRole,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String
)
