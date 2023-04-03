package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.create.allowedMentions
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.azisaba.spicyazisababot.GitHubUser
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.scheduleAtFixedRateBlocking
import java.util.Timer

object LinkGitHubCommand : CommandHandler {
    private val timer = Timer()
    internal val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        val proxyUrl = BotSecretConfig.config.proxyUrl
        val proxyAuthorization = BotSecretConfig.config.proxyAuthorization

        if (proxyUrl.isNotBlank()) {
            engine {
                proxy = ProxyBuilder.http(proxyUrl)
            }

            if (proxyAuthorization.isNotBlank()) {
                defaultRequest {
                    header(HttpHeaders.ProxyAuthorization, "Basic $proxyAuthorization")
                }
            }
        }
    }

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferEphemeralResponse()
        try {
            val clientId = BotSecretConfig.config.githubClientId
            if (clientId.isBlank()) {
                defer.respond { content = "GitHub連携機能が有効になっていません。" }
                return
            } else {
                createTable()
                val responseObject =
                    client.post("https://github.com/login/device/code?client_id=$clientId&scope=read:user")
                val res =
                    responseObject.bodyAsText()
                        .split("&")
                        .map { it.decodeURLQueryComponent() }
                        .map { it.split("=") }
                        .associate { it[0] to it[1] }
                val message = defer.respond {
                    content = """
                        GitHubアカウントを連携するには、以下のURLにアクセスしてください。
                        <${res["verification_uri"]}>
                        コード: `${res["user_code"]}`
                        有効期間: ${res["expires_in"]}秒
                    """.trimIndent()
                }
                val interval = res["interval"]?.toLong() ?: 5
                timer.scheduleAtFixedRateBlocking(1000 + interval * 1000, 1000 + interval * 1050) {
                    try {
                        val response =
                            client.post("https://github.com/login/oauth/access_token?client_id=$clientId&device_code=${res["device_code"]}&grant_type=urn:ietf:params:oauth:grant-type:device_code")
                        val resToken = response.bodyAsText()
                            .split("&")
                            .map { it.decodeURLQueryComponent() }
                            .map { it.split("=") }
                            .associate { it[0] to it[1] }
                        val error = resToken["error"]
                        if (error != null) {
                            if (error != "authorization_pending" && error != "slow_down") {
                                cancel()
                                message.edit {
                                    content = "GitHubアカウントの連携に失敗しました。\nエラーコード: `$error`"
                                }
                            }
                            return@scheduleAtFixedRateBlocking
                        }
                        cancel()
                        val accessToken = resToken["access_token"]!!
                        val tokenType = resToken["token_type"]!!
                        val userResponse = client.get("https://api.github.com/user") {
                            header("Accept", "application/vnd.github.v3+json")
                            header("Authorization", "$tokenType $accessToken")
                        }
                        val user: GitHubUser = json.decodeFromString(userResponse.bodyAsText())
                        Util.getConnection().use { connection ->
                            connection.prepareStatement("INSERT INTO `github` (`discord_id`, `github_id`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `github_id` = ?")
                                .use { statement ->
                                    statement.setString(1, interaction.user.id.toString())
                                    statement.setString(2, user.login)
                                    statement.setString(3, user.login)
                                    statement.executeUpdate()
                                }
                        }
                        message.edit {
                            content = "GitHubアカウントを連携しました。"
                        }
                        notifyWebhook(
                            interaction.kord,
                            "${interaction.user.mention} (ID: `${interaction.user.id}`)がGitHubアカウント(`${user.login}`)を連携しました。"
                        )
                    } catch (e: Exception) {
                        cancel()
                        message.edit { content = "処理中にエラーが発生しました。" }
                        return@scheduleAtFixedRateBlocking
                    }
                }
            }
        } catch (e: Exception) {
            defer.respond { content = "処理中にエラーが発生しました。" }
            e.printStackTrace()
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("link-github", "Link GitHub account") {
            description(Locale.JAPANESE, "GitHubアカウントを連携")
        }
    }

    fun createTable() {
        Util.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                        CREATE TABLE IF NOT EXISTS `github` (
                        `discord_id` VARCHAR(255) NOT NULL,
                        `github_id` VARCHAR(255) NOT NULL,
                        PRIMARY KEY (`discord_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun notifyWebhook(kord: Kord, content: String) {
        val webhook = BotSecretConfig.config.githubLinkWebhookUrl
        if (webhook.isBlank()) return
        val path = webhook.split("/")
        val webhookId = path[path.size - 2]
        val webhookToken = path[path.size - 1]
        kord.rest.webhook.executeWebhook(Snowflake(webhookId), webhookToken, false) {
            allowedMentions { }
            this.content = content
        }
    }
}
