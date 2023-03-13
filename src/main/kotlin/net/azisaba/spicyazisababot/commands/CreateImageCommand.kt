package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.ReactionEmoji
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
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optString
import java.io.ByteArrayInputStream

object CreateImageCommand : CommandHandler {
    private val client = HttpClient(CIO) {
        engine {
            this.requestTimeout = 1000 * 60 * 10
        }
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val prompt = interaction.optString("prompt") ?: return
        val n = interaction.optLong("n") ?: 1
        val size = interaction.optString("size") ?: "1024x1024"
        val defer = interaction.deferPublicResponse()
        try {
            val moderationResponse = client.post("https://api.openai.com/v1/moderations") {
                setBody(LinkGitHubCommand.json.encodeToString(PostModerationBody(prompt)))
                header("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
                header("Content-Type", "application/json")
            }.bodyAsText().let { LinkGitHubCommand.json.decodeFromString(PostModerationResponse.serializer(), it) }
            if (moderationResponse.results.any { it.flagged }) {
                defer.respond {
                    content = "（入力された文章は不適切と判断されたため、生成されません）"
                }.apply { message.addReaction(ReactionEmoji.Unicode("⚠️")) }
                return
            }
            val response = client.post("https://api.openai.com/v1/images/generations") {
                setBody(
                    LinkGitHubCommand.json.encodeToString(CreateImageRequest(prompt, n.toInt(), size, "b64_json"))
                )
                header("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
                header("Content-Type", "application/json")
            }
            val responseAsText = response.bodyAsText()
            try {
                val image = LinkGitHubCommand.json.decodeFromString(ResponseImage.serializer(), responseAsText)
                defer.respond {
                    image.data.forEachIndexed { index, data ->
                        val bytes = data.toByteArray()
                        val stream = ByteArrayInputStream(bytes)
                        val name = "image_${index + 1}.png"
                        addFile(name, stream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                defer.respond {
                    content = "エラーが発生しました。\n${e.message}\n```json\n${responseAsText}\n```"
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
        builder.input("create-image", "ChatGPTを使って文章を生成します。") {
            string("prompt", "画像を生成するためのテキストを入力してください。") {
                required = true
            }
            number("n", "生成する画像の枚数を指定します。(1～10、デフォルト1)") {
                required = false
                minValue = 1.0
                maxValue = 10.0
            }
            string("size", "生成する画像のサイズを指定します。(デフォルト1024x1024)") {
                required = false
                choice("256x256", "256x256")
                choice("512x512", "512x512")
                choice("1024x1024", "1024x1024")
            }
        }
    }
}

@Serializable
private data class CreateImageRequest(
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerialName("response_format")
    val responseFormat: String,
)

@Serializable
private data class ResponseImage(
    val created: Long,
    val data: List<ResponseImageData>,
)

@Serializable
private data class ResponseImageData(
    @SerialName("b64_json")
    val b64Json: String,
) {
    fun toByteArray(): ByteArray = b64Json.decodeBase64Bytes()
}
