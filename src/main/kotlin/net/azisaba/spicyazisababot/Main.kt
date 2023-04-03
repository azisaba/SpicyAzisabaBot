@file:JvmName("MainKt")
package net.azisaba.spicyazisababot

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.toList
import net.azisaba.spicyazisababot.commands.AddRolesCommand
import net.azisaba.spicyazisababot.commands.BuildCommand
import net.azisaba.spicyazisababot.commands.ChatGPTCommand
import net.azisaba.spicyazisababot.commands.CheckGitHubCommand
import net.azisaba.spicyazisababot.commands.CleanUserMessagesCommand
import net.azisaba.spicyazisababot.commands.CommandHandler
import net.azisaba.spicyazisababot.commands.CopyTableCommand
import net.azisaba.spicyazisababot.commands.CountRoleMembersCommand
import net.azisaba.spicyazisababot.commands.CreateAttachmentsTableCommand
import net.azisaba.spicyazisababot.commands.CreateImageCommand
import net.azisaba.spicyazisababot.commands.CreateMessageCommand
import net.azisaba.spicyazisababot.commands.CreateMessagesTableCommand
import net.azisaba.spicyazisababot.commands.CustomBuildCommand
import net.azisaba.spicyazisababot.commands.CustomCommand
import net.azisaba.spicyazisababot.commands.CveCommand
import net.azisaba.spicyazisababot.commands.EditMessageCommand
import net.azisaba.spicyazisababot.commands.GlobalPermissionsCommand
import net.azisaba.spicyazisababot.commands.LinkGitHubCommand
import net.azisaba.spicyazisababot.commands.PermissionsCommand
import net.azisaba.spicyazisababot.commands.StatsCommand
import net.azisaba.spicyazisababot.commands.ToDBCommand
import net.azisaba.spicyazisababot.commands.TranslateRomajiCommand
import net.azisaba.spicyazisababot.commands.UnlinkGitHubCommand
import net.azisaba.spicyazisababot.commands.UploadAttachmentCommand
import net.azisaba.spicyazisababot.config.BotConfig
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.replaceWithMap

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    // load config
    BotConfig
    BotSecretConfig

    // init client
    val client = Kord(BotSecretConfig.config.token)

    // builtin commands
    val commands = mapOf(
        "stats" to StatsCommand,
        "translate-romaji" to TranslateRomajiCommand,
        "countrolemembers" to CountRoleMembersCommand,
        "permissions" to PermissionsCommand,
        "build" to BuildCommand,
        "custom-build" to CustomBuildCommand,
        "add-roles" to AddRolesCommand,
        "create-message" to CreateMessageCommand,
        "edit-message" to EditMessageCommand,
        "create-attachments-table" to CreateAttachmentsTableCommand,
        "copy-table" to CopyTableCommand,
        "create-messages-table" to CreateMessagesTableCommand,
        "upload-attachment" to UploadAttachmentCommand,
        "to-db" to ToDBCommand,
        "link-github" to LinkGitHubCommand,
        "unlink-github" to UnlinkGitHubCommand,
        "check-github" to CheckGitHubCommand,
        "chatgpt" to ChatGPTCommand,
        "reply" to ChatGPTCommand,
        "create-image" to CreateImageCommand,
        "clean-user-messages" to CleanUserMessagesCommand,
        "cve" to CveCommand,
        "gpedit" to GlobalPermissionsCommand,
    ) + BotConfig.config.customCommands.associate { it.name to CustomCommand(it) } // custom commands

    fun getEnabledCommands(): Map<String, CommandHandler> =
        commands.filterKeys { it !in BotConfig.config.disabledCommands }

    // delete commands
    /*
    client.getGlobalApplicationCommands().collect {
        if (it.name !in getEnabledCommands().keys) {
            it.delete()
        }
    }
    */

    client.createGlobalApplicationCommands {
        getEnabledCommands().values.distinct().forEach { it.register(this) }
    }

    client.on<ApplicationCommandInteractionCreateEvent> {
        if (interaction.user.isBot) return@on
        getEnabledCommands().forEach { (name, command) ->
            if (interaction.invokedCommandName == name) {
                command.handle(interaction)
            }
        }
    }

    client.on<MemberJoinEvent> {
        if (member.isBot) return@on
        for (config in BotConfig.config.welcomeMessages) {
            if (config.channelId.isBlank()) continue
            val channel = client.getChannel(Snowflake(config.channelId)) as? TextChannel ?: continue
            if (channel.guildId != member.guildId) continue
            val replaceMap = mapOf(
                "{mention}" to member.mention,
                "{tag}" to member.tag,
            )
            channel.createMessage(config.messageLines.joinToString("\n").replaceWithMap(replaceMap))
        }
    }

    client.on<ReadyEvent> {
        println("Logged in as ${kord.getSelf().tag}!")
        guilds.forEach { guild -> guild.requestMembers() }
        println("Fetched all members in ${this.guilds.size} guilds.")
    }

    client.on<MemberLeaveEvent> {
        for (config in BotConfig.config.leaveMessages) {
            if (config.channelId.isBlank()) continue
            val channel = client.getChannel(Snowflake(config.channelId)) as? TextChannel ?: continue
            if (channel.guildId != guildId) return@on
            val currentGitHubConnection = Util.getConnection().use { connection ->
                connection.prepareStatement("SELECT `github_id` FROM `github` WHERE `discord_id` = ?").use { statement ->
                    statement.setString(1, user.id.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("github_id")
                        } else {
                            null
                        }
                    }
                }
            }
            val replaceMap = mutableMapOf(
                "user.tag" to user.tag,
                "user.id" to user.id,
                "user.isBot" to user.isBot,
                "github" to currentGitHubConnection,
                "old.nickname" to old?.nickname,
                "old.roleNames" to old?.roles?.toList()?.joinToString(", ") { it.name },
                "old.roleIds" to old?.roleIds?.joinToString(", "),
                "old.joinedAt.epochSeconds" to old?.joinedAt?.epochSeconds,
            )
            channel.createMessage(config.messageLines.joinToString("\n").replaceWithMap(replaceMap))
        }
    }

    client.login {
        this.intents = Intents(
            Intent.GuildMembers, // privileged
            Intent.GuildMessages,
            Intent.DirectMessages,
            Intent.GuildVoiceStates,
            Intent.GuildPresences, // privileged
            Intent.MessageContent, // privileged
        )
    }
}
