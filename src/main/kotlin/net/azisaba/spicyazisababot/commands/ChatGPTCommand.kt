package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.response.edit
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
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.azisaba.spicyazisababot.config.BotConfig
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import net.azisaba.spicyazisababot.permission.GlobalPermissionNode
import net.azisaba.spicyazisababot.permission.PermissionManager
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.modal
import net.azisaba.spicyazisababot.util.Util.optBoolean
import net.azisaba.spicyazisababot.util.Util.optDouble
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optString
import java.io.ByteArrayInputStream

object ChatGPTCommand : CommandHandler {
    private val conversations = mutableMapOf<Snowflake, ConversationData>() // user id to messages
    private val client = HttpClient(CIO) {
        engine {
            this.requestTimeout = 1000 * 60 * 10
        }
    }
    private val lastUpdated = mutableMapOf<String, Long>()

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        // clear conversations if not reply
        if (interaction.invokedCommandName != "reply") {
            conversations.remove(interaction.user.id)
        }
        // handle "text" in parameter
        val temperature = interaction.optDouble("temperature") ?: 1.0
        val role = interaction.optString("role") ?: "user"
        val maxTokens = interaction.optLong("max_tokens") ?: 2000
        val force = interaction.optBoolean("force") ?: false
        val systemPreset = interaction.optString("system-preset")
        val system = BotConfig.config.chatgptPresets[systemPreset] ?: interaction.optString("system")
        val model = interaction.optString("model")
        interaction.optString("text")?.apply {
            return handle(interaction, this, temperature, role, maxTokens, force, system, model)
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
            handle(this, this.textInputs["text"]!!.value!!, temperature, role, maxTokens, force, system, model)
        }
    }

    private suspend fun handle(
        interaction: ActionInteraction,
        text: String,
        temperature: Double,
        role: String,
        maxTokens: Long,
        force: Boolean,
        system: String?,
        modelNullable: String?,
    ) {
        val defer = interaction.deferPublicResponse()
        val model = modelNullable ?: conversations[interaction.user.id]?.model ?: "gpt-3.5-turbo"
        val fetchingReaction = ReactionEmoji.Unicode("♻")
        try {
            // Perform access check based on global permission.
            // If user has permission of chatgpt.model.all, the user can use all models.
            // Otherwise, the check is performed based on the model. (chatgpt.model.{model})
            val allowAll =
                PermissionManager.globalCheck(interaction.user.id, GlobalPermissionNode.ChatGPTModelAll).value?.apply {
                    if (!this) {
                        defer.respond { content = "このコマンドを使用する権限がありません。" }
                        return
                    }
                }
            if (allowAll != true) {
                val node = GlobalPermissionNode.nodeMap["chatgpt.model.$model"]
                if (node == null) {
                    defer.respond { content = "このModelを使用する権限がありません。" }
                    return
                }
                if (PermissionManager.globalCheck(interaction.user.id, node).value != true) {
                    defer.respond { content = "このModelを使用する権限がありません。" }
                    return
                }
            }
            interaction.channel.getGuildOrNull()?.let { guild ->
                // Perform access check based on server permission.
                // If user has permission of chatgpt.model.all, the user can use all models.
                // Otherwise, the check is performed based on the model. (chatgpt.model.{model})
                val allowAllOnServer =
                    PermissionManager.check(guild.getMember(interaction.user.id), "chatgpt.model.all").value?.apply {
                        if (!this) {
                            defer.respond { content = "このコマンドを使用する権限がありません。" }
                            return
                        }
                    }
                if (allowAllOnServer != true) {
                    if (PermissionManager.check(
                            guild.getMember(interaction.user.id),
                            "chatgpt.model.$model"
                        ).value != true
                    ) {
                        defer.respond {
                            content = "このModelを使用する権限がありません。`chatgpt.model.$model`もしくは`chatgpt.model.all`が必要です。"
                        }
                        return
                    }
                }
            }
            // check sanity
            val moderationResponse = client.post("https://api.openai.com/v1/moderations") {
                setBody(LinkGitHubCommand.json.encodeToString(PostModerationBody(text)))
                header("Authorization", "Bearer ${BotSecretConfig.config.openAIApiKey}")
                header("Content-Type", "application/json")
            }.bodyAsText().let { LinkGitHubCommand.json.decodeFromString(PostModerationResponse.serializer(), it) }
            val emoji = if (moderationResponse.results.any { it.flagged }) {
                // message is flagged
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
            val thisMessage = ContentWithRole(role, text)
            if (system != null) {
                conversations.computeIfAbsent(interaction.user.id) { ConversationData(model) }
                    .conversation.add(ContentWithRole("system", system))
            }
            conversations.computeIfAbsent(interaction.user.id) { ConversationData(model) }
                .conversation.add(thisMessage)
            if (role == "assistant") {
                defer.respond {
                    content = text
                }
                return
            }
            var content = ""
            val msg = defer.respond { this.content = "*生成中...*" }
            if (emoji != null) {
                msg.message.addReaction(emoji)
            }
            msg.message.addReaction(fetchingReaction)
            // execute the api
            Util.createPostEventsFlow(
                "https://api.openai.com/v1/chat/completions",
                LinkGitHubCommand.json.encodeToString(
                    PostBody(
                        model,
                        maxTokens.toInt(),
                        temperature,
                        conversations[interaction.user.id]!!.conversation,
                        true,
                    )
                ),
                mapOf(
                    "Authorization" to "Bearer ${BotSecretConfig.config.openAIApiKey}",
                    "Content-Type" to "application/json",
                ),
            ).collect {
                if (it.data == "[DONE]") {
                    if (content.length > 2000) {
                        msg.edit {
                            content = "（生成された文章が2000文字を超えたため、ファイルとして送信します。）"
                            ByteArrayInputStream(content.toByteArray()).use { stream ->
                                addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                            }
                        }
                    } else {
                        msg.edit {
                            this.content = content
                        }
                    }
                    msg.message.deleteOwnReaction(fetchingReaction)
                    conversations[interaction.user.id]!!.conversation.add(ContentWithRole("assistant", content))
                    return@collect
                }
                val response = LinkGitHubCommand.json.decodeFromString<StreamResponse>(it.data)
                content += response.choices[0].delta.content
                if (content.length in 1..2000 && System.currentTimeMillis() - (lastUpdated[msg.token] ?: 0) > 500) {
                    lastUpdated[msg.token] = System.currentTimeMillis()
                    msg.edit {
                        this.content = BuildCommand.trimOutput(content, 2000)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defer.respond {
                content = "エラーが発生しました。\n${e.message}"
            }.message.deleteOwnReaction(fetchingReaction)
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        val build: GlobalChatInputCreateBuilder.() -> Unit = {
            dmPermission = false
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
            }
            number("max_tokens", "生成する文章の最大文字数を指定します。(100～4000、デフォルト2000)") {
                required = false
                minValue = 100.0
                maxValue = 4000.0
            }
            boolean("force", "flagされても文章を生成します。") {
                required = false
            }
            string("model", "モデルを指定します") {
                choice("GPT-3.5", "gpt-3.5-turbo")
                choice("gpt-3.5-turbo-0301", "gpt-3.5-turbo-0301")
                choice("GPT-4 (8k)", "gpt-4")
                choice("GPT-4 (32k)", "gpt-4-32k")
            }
        }
        builder.input("reply", "ChatGPTを使って文章を生成します。") {
            build(this)
        }
        builder.input("chatgpt", "ChatGPTを使って文章を生成します。") {
            build(this)
            string("system", "システムの指示") {
                required = false
                minLength = 1
            }
            string("system-preset", "システムの指示のプリセット") {
                required = false
                minLength = 1
                BotConfig.config.chatgptPresets.keys.forEach { choice(it, it) }
            }
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
    val stream: Boolean,
)

@Serializable
private data class ContentWithRole(val role: String, val content: String)

@Serializable
private data class ConversationData(
    val model: String,
    val conversation: MutableList<ContentWithRole> = mutableListOf(),
)

@Serializable
private data class StreamResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<StreamResponseChoice>,
)

@Serializable
private data class StreamResponseChoice(
    val delta: StreamResponseChoiceDelta,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String?,
)

@Serializable
private data class StreamResponseChoiceDelta(
    val content: String = "",
    val role: String = "assistant",
)
