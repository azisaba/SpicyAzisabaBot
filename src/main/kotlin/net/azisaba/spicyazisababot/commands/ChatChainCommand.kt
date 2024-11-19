package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.embed
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
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

object ChatChainCommand : CommandHandler {
    private val client = HttpClient(OkHttp)
    private val lastUpdated = mutableMapOf<String, Long>()

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val temperature = interaction.optDouble("temperature") ?: 1.0
        val maxTokens = interaction.optLong("max_tokens") ?: 2000
        val force = interaction.optBoolean("force") ?: false
        val systemPreset = interaction.optString("system-preset-override")
        val system =
            BotConfig.config.chatgptPresets[systemPreset]
                ?: interaction.optString("system-override")
                ?: BotConfig.config.chatChain.prompt
        val model = interaction.optString("model")
        interaction.optString("text")?.apply {
            return handle(interaction, this, temperature, maxTokens, force, system, model)
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
            handle(this, this.textInputs["text"]!!.value!!, temperature, maxTokens, force, system, model)
        }
    }

    private suspend fun handle(
        interaction: ActionInteraction,
        text: String,
        temperature: Double,
        maxTokens: Long,
        force: Boolean,
        system: String,
        modelNullable: String?,
    ) {
        val defer = interaction.deferPublicResponse()

        // check if we're running in DMs
        if (interaction.channel.getGuildOrNull() == null) {
            val allowedInDM = PermissionManager.globalCheck(interaction.user.id, GlobalPermissionNode.ChatGPTInDM).value ?: false
            if (!allowedInDM) {
                defer.respond { content = "このコマンドを使用する権限がありません。" }
                return
            }
        }

        val model = modelNullable ?: "gpt-3.5-turbo"
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
            if (allowAll == null) {
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
                if (BotSecretConfig.config.openAIOrgId != null) {
                    header("OpenAI-Organization", BotSecretConfig.config.openAIOrgId)
                }
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
            val list = mutableListOf<ContentWithRole>()
            var content = "Input: $text\n\nPrompt: $system\n"
            val msg = defer.respond { this.content = content }
            if (emoji != null) {
                msg.message.addReaction(emoji)
            }
            msg.message.addReaction(fetchingReaction)
            var currentChain = BotConfig.config.chatChain
            while (true) {
                // add conversations
                if (list.isEmpty()) {
                    list.add(ContentWithRole("system", system))
                    list.add(ContentWithRole("user", text))
                } else {
                    list.clear()
                    list.add(ContentWithRole("system", currentChain.prompt))
                    list.add(ContentWithRole("user", text))
                    content += "Prompt: ${currentChain.prompt}\n"
                }
                content += "Output: "
                // execute the api
                var output = ""
                Util.createPostEventsFlow(
                    "https://api.openai.com/v1/chat/completions",
                    LinkGitHubCommand.json.encodeToString(
                        PostBody(
                            model,
                            maxTokens.toInt(),
                            temperature,
                            list,
                            true,
                            interaction.user.id.toString(),
                        )
                    ),
                    mapOf(
                        "Authorization" to "Bearer ${BotSecretConfig.config.openAIApiKey}",
                        "Content-Type" to "application/json",
                    ) + BotSecretConfig.config.getExtraOpenAIHeaders(),
                ).collect {
                    if (it.data == "[DONE]") {
                        list.add(ContentWithRole("assistant", output))
                        return@collect
                    }
                    val response = LinkGitHubCommand.json.decodeFromString<StreamResponse>(it.data)
                    output += response.choices[0].delta.content
                    content += response.choices[0].delta.content
                    if (System.currentTimeMillis() - (lastUpdated[msg.token] ?: 0) < 500) return@collect
                    lastUpdated[msg.token] = System.currentTimeMillis()
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
                content += "\n\n"
                currentChain = currentChain.chain[output.trim('\n', ' ')] ?: break
            }
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
                ByteArrayInputStream(content.toByteArray()).use { stream ->
                    addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                }
            }
            msg.message.deleteOwnReaction(fetchingReaction)
        } catch (e: Exception) {
            e.printStackTrace()
            defer.respond {
                content = "エラーが発生しました。\n${e.message}"
            }.message.deleteOwnReaction(fetchingReaction)
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("chatchain", "ChatGPTを使って文章を生成します。") {
            string("text", "文章を生成するためのテキストを入力してください。") {
                required = false
            }
            number("temperature", "生成する文章の温度を指定します。") {
                required = false
                minValue = 0.0
                maxValue = 1.0
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
            string("system-override", "システムの指示") {
                required = false
                minLength = 1
            }
            string("system-preset-override", "システムの指示のプリセット") {
                required = false
                minLength = 1
                BotConfig.config.chatgptPresets.keys.forEach { choice(it, it) }
            }
        }
    }
}
