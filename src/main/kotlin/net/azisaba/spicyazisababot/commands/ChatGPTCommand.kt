package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.boolean
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
import net.azisaba.spicyazisababot.util.Util.modal
import net.azisaba.spicyazisababot.util.Util.optBoolean
import net.azisaba.spicyazisababot.util.Util.optDouble
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optString
import java.io.ByteArrayInputStream

object ChatGPTCommand : CommandHandler {
    private val conversations = mutableMapOf<Snowflake, MutableList<ContentWithRole>>() // user id to messages
    private val client = HttpClient(CIO) {
        engine {
            this.requestTimeout = 1000 * 60 * 10
        }
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        // clear conversations if not reply
        if (interaction.invokedCommandName != "reply") {
            conversations[interaction.user.id]?.clear()
        }
        // handle "text" in parameter
        val temperature = interaction.optDouble("temperature") ?: 1.0
        val role = interaction.optString("role") ?: "user"
        val maxTokens = interaction.optLong("max_tokens") ?: 2000
        val force = interaction.optBoolean("force") ?: false
        interaction.optString("text")?.apply {
            return handle(interaction, this, temperature, role, maxTokens, force)
        }

        // or ask for text input
        interaction.modal("ChatGPT (${interaction.invokedCommandName.replaceFirstChar { it.uppercase() }})", {
            actionRow {
                textInput(TextInputStyle.Paragraph, "text", "テキスト") {
                    required = true
                    placeholder = "文章を生成するためのテキストを入力してください。"
                }
            }
        }) {
            handle(this, this.textInputs["text"]!!.value!!, temperature, role, maxTokens, force)
        }
    }

    private suspend fun handle(interaction: ActionInteraction, text: String, temperature: Double, role: String, maxTokens: Long, force: Boolean) {
        val defer = interaction.deferPublicResponse()
        try {
            val moderationResponse = client.post("https://api.openai.com/v1/moderations") {
                setBody(LinkGitHubCommand.json.encodeToString(PostModerationBody(text)))
                header("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
                header("Content-Type", "application/json")
            }.bodyAsText().let { LinkGitHubCommand.json.decodeFromString(PostModerationResponse.serializer(), it) }
            val emoji = if (moderationResponse.results.any { it.flagged }) {
                if (!force) {
                    defer.respond {
                        content = "（入力された文章は不適切と判断されたため、生成されません）"
                    }.apply { message.addReaction(ReactionEmoji.Unicode("⚠️")) }
                    return
                }
                ReactionEmoji.Unicode("⚠️")
            } else {
                null
            }
            val response = client.post("https://api.openai.com/v1/chat/completions") {
                val thisMessage = ContentWithRole(role, text)
                conversations.computeIfAbsent(interaction.user.id) { mutableListOf() }.add(thisMessage)
                setBody(
                    LinkGitHubCommand.json.encodeToString(
                        PostBody(
                            "gpt-3.5-turbo",
                            maxTokens.toInt(),
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
                content = if (result.length > 2000) {
                    addFile("output.md", ByteArrayInputStream(result.toByteArray()))
                    "（生成された文章が2000文字を超えたため、ファイルとして送信します。）"
                } else {
                    result
                }
            }.apply {
                if (emoji != null) {
                    message.addReaction(emoji)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defer.respond {
                content = "エラーが発生しました。\n${e.message}"
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        val build: GlobalChatInputCreateBuilder.() -> Unit = {
            string("text", "文章を生成するためのテキストを入力してください。") {
                required = false
            }
            number("temperature", "生成する文章の温度を指定します。") {
                required = false
                minValue = 0.0
                maxValue = 1.0
            }
            string("role", "文章を生成するためのテキストの役割を指定します。") {
                required = false
                choice("ユーザー", "user")
                choice("アシスタント", "assistant")
                choice("システム", "system")
            }
            number("max_tokens", "生成する文章の最大文字数を指定します。(100～4000、デフォルト2000)") {
                required = false
                minValue = 100.0
                maxValue = 4000.0
            }
            boolean("force", "flagされても文章を生成します。") {
                required = false
            }
        }
        builder.input("reply", "ChatGPTを使って文章を生成します。") {
            build(this)
        }
        builder.input("chatgpt", "ChatGPTを使って文章を生成します。") {
            build(this)
        }
    }
}

@Serializable
internal data class PostModerationBody(
    val input: String,
)

@Serializable
internal data class PostModerationResponse(
    val id: String,
    val model: String,
    val results: List<PostModerationResponseResults>,
)

@Serializable
internal data class PostModerationResponseResults(
    val flagged: Boolean,
)

@Serializable
private data class PostBody(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Double,
    val messages: List<ContentWithRole>,
)

@Serializable
private data class ContentWithRole(val role: String, val content: String)

@Serializable
private data class ResponseChoice(
    val message: ContentWithRole,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String?
)
