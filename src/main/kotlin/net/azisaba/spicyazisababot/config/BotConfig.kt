package net.azisaba.spicyazisababot.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.charleskorn.kaml.YamlConfiguration
import dev.kord.common.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule
import net.azisaba.spicyazisababot.config.command.CustomCommandConfig
import net.azisaba.spicyazisababot.config.command.CustomCommandResponseEmbed
import net.azisaba.spicyazisababot.config.command.CustomCommandResponseText
import net.azisaba.spicyazisababot.config.command.CustomCommandConfigToggleRole
import net.azisaba.spicyazisababot.config.command.CustomCommandDefinition
import net.azisaba.spicyazisababot.config.command.CustomCommandResponse
import java.io.File

@Serializable
data class BotConfig(
    @YamlComment(
        "次回起動時にbot.ymlを更新するかどうかを指定します。",
        "trueにした場合、次回起動時にbot.ymlは上書きされ、overwrite設定は自動的にfalseになります。",
    )
    var overwrite: Boolean = false,
    @YamlComment("discord-message-viewerのURLを指定します。(最後の'/'を除く)")
    val messageViewerBaseUrl: String = "https://messageviewer.azisaba.net",
    @YamlComment("無効にするコマンドを指定します。")
    val disabledCommands: List<String> = listOf("clean-user-messages"),
    @YamlComment("参加時のメッセージを指定します。")
    val welcomeMessages: List<WelcomeMessageConfig> = listOf(WelcomeMessageConfig()),
    @YamlComment("退出時のメッセージを指定します。")
    val leaveMessages: List<LeaveMessageConfig> = listOf(LeaveMessageConfig()),
    @YamlComment("/chatgptのsystem-presetで使用できるプリセットを設定します。")
    val chatgptPresets: Map<String, String> = mutableMapOf("assistant" to "You are a helpful assistant."),
    val remindTimezone: String = "Asia/Tokyo",
    val customCommands: List<CustomCommandDefinition> = listOf(
        CustomCommandDefinition(
            "vote",
            "pls vote",
            CustomCommandDefinition.ResponseType.Ephemeral,
            CustomCommandResponseEmbed(
                title = "投票URL",
                description = "毎日投票しよう！",
                author = CustomCommandResponseEmbed.Author("SpicyAzisabaBot"),
                url = "https://azisaba.net/",
                color = Color(0x00FF00),
                fields = listOf(
                    CustomCommandResponseEmbed.Field(
                        "Japan Minecraft Servers",
                        "https://minecraft.jp/servers/azisaba.net"
                    ),
                    CustomCommandResponseEmbed.Field(
                        "monocraft",
                        "https://monocraft.net/servers/xWBVrf1nqB2P0LxlMm2v"
                    ),
                ),
            ),
        ),
        CustomCommandDefinition(
            "toggle-role",
            "ロールを切り替えます",
            CustomCommandDefinition.ResponseType.Ephemeral,
            CustomCommandConfigToggleRole(
                mapOf("874990784657633301" to "875010413002096670"),
                responseOffToOn = CustomCommandResponseText("ロールを付与しました"),
                responseOnToOff = CustomCommandResponseText("ロールを剥奪しました"),
            )
        ),
    ),
    val chatChain: ChatChain = ChatChain("You are a helpful assistant.", mapOf("minecraft" to ChatChain("You are a helpful assistant."))),
) {
    companion object {
        private val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = true, strictMode = false), serializersModule = SerializersModule {
            polymorphic(CustomCommandConfig::class, CustomCommandConfigToggleRole::class, CustomCommandConfigToggleRole.serializer())
            polymorphic(CustomCommandResponse::class, CustomCommandResponseText::class, CustomCommandResponseText.serializer())
            polymorphic(CustomCommandResponse::class, CustomCommandResponseEmbed::class, CustomCommandResponseEmbed.serializer())
        })

        val config: BotConfig = File("config/bot.yml").let { file ->
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (!file.exists()) file.writeText(yaml.encodeToString(BotConfig()))
            yaml.decodeFromString(serializer(), file.readText())
        }

        init {
            if (config.overwrite) {
                config.overwrite = false
                File("config/bot.yml").writeText(yaml.encodeToString(config))
            }
        }
    }
}
