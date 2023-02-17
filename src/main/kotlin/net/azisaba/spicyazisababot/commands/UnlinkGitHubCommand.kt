package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.spicyazisababot.util.Util

object UnlinkGitHubCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferEphemeralResponse()
        try {
            LinkGitHubCommand.createTable()
            val currentId = Util.getConnection().use { connection ->
                connection.prepareStatement("SELECT `github_id` FROM `github` WHERE `discord_id` = ?").use { statement ->
                    statement.setString(1, interaction.user.id.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("github_id")
                        } else {
                            null
                        }
                    }
                }
            }
            if (currentId == null) {
                defer.respond { content = "GitHubアカウントは未連携です。" }
                return
            }
            Util.getConnection().use { connection ->
                connection.prepareStatement("DELETE FROM `github` WHERE `discord_id` = ?").use { statement ->
                    statement.setString(1, interaction.user.id.toString())
                    statement.executeUpdate()
                }
            }
            defer.respond { content = "GitHubアカウントの連携を解除しました。" }
            LinkGitHubCommand.notifyWebhook(
                interaction.kord,
                "${interaction.user.mention} (ID: `${interaction.id}`)がGitHubアカウント(`$currentId`)の連携を解除しました。"
            )
        } catch (e: Exception) {
            defer.respond { content = "処理中にエラーが発生しました。" }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("unlink-github", "Unlink GitHub account") {
            description(Locale.JAPANESE, "GitHubアカウントの連携を解除")
        }
    }
}
