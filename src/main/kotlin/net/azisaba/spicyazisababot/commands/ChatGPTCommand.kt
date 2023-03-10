package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
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
import net.azisaba.spicyazisababot.util.Util.optString

object ChatGPTCommand : CommandHandler {
    private val client = HttpClient(CIO) {
        engine {
            this.requestTimeout = 1000 * 60 * 10
        }
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val text = interaction.optString("text")!!
        val defer = interaction.deferPublicResponse()
        try {
            val response = client.post("https://api.openai.com/v1/chat/completions") {
                this.setBody(
                    LinkGitHubCommand.json.encodeToString(
                        PostBody(
                            "gpt-3.5-turbo",
                            2000,
                            1.0,
                            listOf(
                                PostBodyMessage("user", text),
                            )
                        )
                    )
                )
                header("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
                header("Content-Type", "application/json")
            }
            val body = LinkGitHubCommand.json.parseToJsonElement(response.bodyAsText())
            val choices = (body.jsonObject["choices"] ?: error("choices is not present in json: $body")).jsonArray
            val choice = LinkGitHubCommand.json.decodeFromJsonElement(ResponseChoice.serializer(), choices[0].jsonObject)
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
        builder.input("chatgpt", "ChatGPTを使って文章を生成します。") {
            string("text", "文章を生成するためのテキストを入力してください。") {
                required = true
            }
        }
    }
}

@Serializable
private data class PostBody(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Double,
    val messages: List<PostBodyMessage>,
)

@Serializable
private data class PostBodyMessage(val role: String, val content: String)

@Serializable
private data class ResponseChoice(
    val message: ResponseMessage,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String
)

@Serializable
private data class ResponseMessage(val role: String, val content: String)
