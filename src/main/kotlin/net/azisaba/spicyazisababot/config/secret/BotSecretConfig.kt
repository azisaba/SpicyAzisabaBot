package net.azisaba.spicyazisababot.config.secret

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

@Serializable
data class BotSecretConfig(
    @YamlComment("Discord Botのトークンを指定します。")
    val token: String = "<enter discord bot token here>",
    @YamlComment("OpenAIのAPIキーを指定します。")
    val openAIApiKey: String = "",
    @YamlComment("OpenAIの組織ID")
    val openAIOrgId: String? = null,
    @YamlComment(
        "/build, /custom-buildコマンドでdockerのコンテナを管理するために使用されるDocker APIの場所(ソケットなど)を指定します。",
        "例: tcp://localhost:2375 または unix:///var/run/docker.sock など",
        "空の場合は/build, /custom-buildコマンドが使用できなくなります。",
    )
    val dockerHost: String = "",
    @YamlComment(
        "/build, /custom-buildでリポジトリ情報を取得するために使用されるPersonal Access Tokenを指定します。",
        "空の場合はログインせずに取得しますが、APIの呼び出し回数が大幅に制限されます。",
    )
    val githubToken: String = "",
    @YamlComment(
        "/link-githubコマンドでGitHubアカウントを連携するために使用されるクライアントIDを指定します。",
        "空の場合は/link-githubコマンドが使用できなくなります。",
    )
    val githubClientId: String = "",
    @YamlComment("/link-github, /unlink-githubを使用したときに通知されるURLを指定します。")
    val githubLinkWebhookUrl: String = "",
    @YamlComment(
        "/link-githubに使用されるプロキシのURLを指定します。",
        "空の場合はプロキシとproxyAuthorizationは使用されません。",
    )
    val proxyUrl: String = "",
    @YamlComment(
        "/link-githubに使用されるプロキシの認証を設定します。先頭にBasicがつきます。",
        "適用されるヘッダー: Authorization: Basic {proxyAuthorization}",
        "空の場合は認証を設定しません。",
    )
    val proxyAuthorization: String = "",
    @YamlComment("attachmentsRootUrlのX-Auth-Keyヘッダーを指定します")
    val attachmentsSecret: String = "",
    val database: DatabaseConfig = DatabaseConfig(),
) {
    companion object {
        private val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = true, strictMode = false))

        val config: BotSecretConfig = File("config/secret.yml").let { file ->
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (!file.exists()) file.writeText(yaml.encodeToString(BotSecretConfig()))
            yaml.decodeFromString(serializer(), file.readText())
        }

        init {
            File("config/secret.yml").writeText(yaml.encodeToString(config))
            if (config.token == "<enter discord bot token here>") {
                println("Bot token is not set! Go to config/ directory and configure things.")
                exitProcess(2)
            }
        }
    }

    fun getExtraOpenAIHeaders(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (openAIOrgId != null) map["OpenAI-Organization"] = openAIOrgId
        return map
    }
}
