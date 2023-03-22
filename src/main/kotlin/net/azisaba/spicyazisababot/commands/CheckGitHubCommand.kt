package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optString

object CheckGitHubCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val id = interaction.optString("id") ?: interaction.user.id.toString()
        val defer = interaction.deferEphemeralResponse()
        try {
            LinkGitHubCommand.createTable()
            val current = Util.getConnection().use { connection ->
                connection.prepareStatement("SELECT `discord_id`, `github_id` FROM `github` WHERE `discord_id` = ? OR `github_id` = ?").use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("discord_id") to resultSet.getString("github_id")
                        } else {
                            null
                        }
                    }
                }
            }
            if (current == null) {
                defer.respond { content = "GitHubアカウントは未連携です。" }
                return
            }
            defer.respond { content = "GitHubアカウントは連携されています。\nDiscord ID: `${current.first}`\nGitHub ID: `${current.second}`" }
        } catch (e: Exception) {
            defer.respond { content = "処理中にエラーが発生しました。" }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("check-github", "Check GitHub account") {
            description(Locale.JAPANESE, "GitHubアカウントの連携を確認")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            string("id", "GitHub or Discord ID") {
                description(Locale.JAPANESE, "確認したいGitHubもしくはDiscordのID")
            }
        }
    }
}
