package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.modify.embed
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
import kotlinx.serialization.json.Json
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
import java.io.File

object ChatGPTCommand : CommandHandler {
    val conversations = mutableMapOf<Snowflake, ConversationData>() // user id or message id to messages

    init {
        // load conversations
        try {
            val text = File("conversations.json").readText()
            conversations.putAll(Json.decodeFromString<Map<Snowflake, ConversationData>>(text))
        } catch (ignored: Exception) {}
    }

    fun saveConversations() {
        try {
            val text = Json.encodeToString<Map<Snowflake, ConversationData>>(conversations)
            File("conversations.json").writeText(text)
        } catch (ignored: Exception) {}
    }

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
        val maxTokens = interaction.optLong("max_tokens")
        val force = interaction.optBoolean("force") ?: false
        val systemPreset = interaction.optString("system-preset")
        val system = BotConfig.config.chatgptPresets[systemPreset] ?: interaction.optString("system")
        val model = interaction.optString("model")
        interaction.optString("text")?.apply {
            val defer = interaction.deferPublicResponse()
            val conversationData = conversations.computeIfAbsent(interaction.user.id) { ConversationData(model ?: "gpt-3.5-turbo") }
            return handle(interaction.channel, interaction.user.id, this, temperature, role, maxTokens, force, system, model, conversationData) {
                defer.respond { content = it }.message
            }
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
            val defer = deferPublicResponse()
            val conversationData = conversations.computeIfAbsent(interaction.user.id) { ConversationData(model ?: "gpt-3.5-turbo") }
            handle(this.channel, this.user.id, this.textInputs["text"]!!.value!!, temperature, role, maxTokens, force, system, model, conversationData) {
                defer.respond { content = it }.message
            }
        }
    }

    suspend fun handle(
        channel: MessageChannelBehavior,
        userId: Snowflake,
        text: String,
        temperature: Double = 1.0,
        role: String = "user",
        maxTokens: Long? = null,
        force: Boolean = true,
        system: String? = null,
        modelNullable: String? = null,
        conversationData: ConversationData = ConversationData("gpt-3.5-turbo"),
        respond: suspend (String) -> Message,
    ) {
        // check if we're running in DMs
        if (channel.getGuildOrNull() == null) {
            val allowedInDM = PermissionManager.globalCheck(userId, GlobalPermissionNode.ChatGPTInDM).value ?: false
            if (!allowedInDM) {
                respond("このコマンドを使用する権限がありません。")
                return
            }
        }

        val model = modelNullable ?: conversationData.model
        val fetchingReaction = ReactionEmoji.Unicode("♻")
        try {
            // Perform access check based on global permission.
            // If user has permission of chatgpt.model.all, the user can use all models.
            // Otherwise, the check is performed based on the model. (chatgpt.model.{model})
            val allowAll =
                PermissionManager.globalCheck(userId, GlobalPermissionNode.ChatGPTModelAll).value?.apply {
                    if (!this) {
                        respond("このコマンドを使用する権限がありません。")
                        return
                    }
                }
            if (allowAll == null) {
                val node = GlobalPermissionNode.nodeMap["chatgpt.model.$model"]
                if (node == null) {
                    respond("このModelを使用する権限がありません。")
                    return
                }
                if (PermissionManager.globalCheck(userId, node).value != true) {
                    respond("このModelを使用する権限がありません。")
                    return
                }
            }
            channel.getGuildOrNull()?.let { guild ->
                // Perform access check based on server permission.
                // If user has permission of chatgpt.model.all, the user can use all models.
                // Otherwise, the check is performed based on the model. (chatgpt.model.{model})
                val allowAllOnServer =
                    PermissionManager.check(guild.getMember(userId), "chatgpt.model.all").value?.apply {
                        if (!this) {
                            respond("このコマンドを使用する権限がありません。")
                            return
                        }
                    }
                if (allowAllOnServer != true) {
                    if (PermissionManager.check(
                            guild.getMember(userId),
                            "chatgpt.model.$model"
                        ).value != true
                    ) {
                        respond("このModelを使用する権限がありません。`chatgpt.model.$model`もしくは`chatgpt.model.all`が必要です。")
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
                    respond("（入力された文章は不適切と判断されたため、生成されません）")
                        .apply { addReaction(ReactionEmoji.Unicode("⚠️")) }
                    return
                }
                ReactionEmoji.Unicode("⚠️")
            } else {
                null
            }
            val thisMessage = ContentWithRole(role, text)
            if (system != null) {
                conversationData.conversation.add(ContentWithRole("system", system))
            }
            conversationData.conversation.add(thisMessage)
            if (role == "assistant") {
                respond(text)
                return
            }
            var content = ""
            val msg = respond("*生成中...*")
            if (emoji != null) {
                msg.addReaction(emoji)
            }
            msg.addReaction(fetchingReaction)
            // execute the api
            Util.createPostEventsFlow(
                "https://api.openai.com/v1/chat/completions",
                LinkGitHubCommand.json.encodeToString(
                    PostBody(
                        model,
                        maxTokens?.toInt(),
                        temperature,
                        conversationData.conversation,
                        true,
                        userId.toString(),
                    )
                ),
                mapOf(
                    "Authorization" to "Bearer ${BotSecretConfig.config.openAIApiKey}",
                    "Content-Type" to "application/json",
                ),
            ).collect {
                if (it.data == "[DONE]") {
                    msg.edit {
                        if (content.length > 2000) {
                            this.content = null
                        } else {
                            this.content = content
                        }
                        if (content.length > 4096) {
                            embed {
                                description = "（生成された文章が4096文字を超えたため、ファイルとして送信します。）"
                            }
                        } else if (content.length > 2000) {
                            embed {
                                description = content
                            }
                        }
                        if (content.length > 500) {
                            ByteArrayInputStream(content.toByteArray()).use { stream ->
                                addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                            }
                        }
                    }
                    msg.deleteOwnReaction(fetchingReaction)
                    conversationData.conversation.add(ContentWithRole("assistant", content))
                    conversations[msg.id] = conversationData
                    conversations[userId] = conversationData
                    saveConversations()
                    return@collect
                }
                val response = LinkGitHubCommand.json.decodeFromString<StreamResponse>(it.data)
                content += response.choices[0].delta.content
                if (System.currentTimeMillis() - (lastUpdated[msg.id.toString()] ?: 0) < 500) return@collect
                lastUpdated[msg.id.toString()] = System.currentTimeMillis()
                if (content.length in 1..2000) {
                    msg.edit {
                        this.content = content
                    }
                } else if (content.isNotEmpty()) {
                    msg.edit {
                        this.content = null
                        embed {
                            description = BuildCommand.trimOutput(content, 4096)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            respond("エラーが発生しました。\n${e.message}").deleteOwnReaction(fetchingReaction)
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
internal data class PostBody(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int?,
    val temperature: Double,
    val messages: List<ContentWithRole>,
    val stream: Boolean,
    val user: String?,
)

@Serializable
data class ContentWithRole(val role: String, val content: String)

@Serializable
data class ConversationData(
    val model: String,
    val conversation: MutableList<ContentWithRole> = mutableListOf(),
)

@Serializable
internal data class StreamResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<StreamResponseChoice>,
)

@Serializable
internal data class StreamResponseChoice(
    val delta: StreamResponseChoiceDelta,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String?,
)

@Serializable
internal data class StreamResponseChoiceDelta(
    val content: String = "",
    val role: String = "assistant",
)
