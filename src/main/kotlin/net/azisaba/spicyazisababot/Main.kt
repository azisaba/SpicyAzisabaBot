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
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.toList
import net.azisaba.spicyazisababot.commands.AddRolesCommand
import net.azisaba.spicyazisababot.commands.BuildCommand
import net.azisaba.spicyazisababot.commands.ChatGPTCommand
import net.azisaba.spicyazisababot.commands.CheckGitHubCommand
import net.azisaba.spicyazisababot.commands.CopyTableCommand
import net.azisaba.spicyazisababot.commands.CountRoleMembersCommand
import net.azisaba.spicyazisababot.commands.CreateAttachmentsTableCommand
import net.azisaba.spicyazisababot.commands.CreateMessageCommand
import net.azisaba.spicyazisababot.commands.CreateMessagesTableCommand
import net.azisaba.spicyazisababot.commands.CustomBuildCommand
import net.azisaba.spicyazisababot.commands.EditMessageCommand
import net.azisaba.spicyazisababot.commands.LinkGitHubCommand
import net.azisaba.spicyazisababot.commands.PermissionsCommand
import net.azisaba.spicyazisababot.commands.StatsCommand
import net.azisaba.spicyazisababot.commands.ToDBCommand
import net.azisaba.spicyazisababot.commands.TranslateRomajiCommand
import net.azisaba.spicyazisababot.commands.UnlinkGitHubCommand
import net.azisaba.spicyazisababot.commands.UploadAttachmentCommand
import net.azisaba.spicyazisababot.commands.VoteCommand
import net.azisaba.spicyazisababot.commands.YouTubeCommand
import net.azisaba.spicyazisababot.messages.CVEMessageHandler
import net.azisaba.spicyazisababot.messages.MArtMessageHandler
import net.azisaba.spicyazisababot.messages.RealProblemChannelHandler
import net.azisaba.spicyazisababot.util.Constant
import net.azisaba.spicyazisababot.util.Util

private val messageHandlers = listOf(
    CVEMessageHandler,
    RealProblemChannelHandler,
    MArtMessageHandler,
)

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    val client = Kord(Util.getEnvOrThrow("BOT_TOKEN"))

    client.createGlobalApplicationCommands {
        StatsCommand.register(this)
        YouTubeCommand.register(this)
        VoteCommand.register(this)
        TranslateRomajiCommand.register(this)
        CountRoleMembersCommand.register(this)
        PermissionsCommand.register(this)
        BuildCommand.register(this)
        CustomBuildCommand.register(this)
        AddRolesCommand.register(this)
        CreateMessageCommand.register(this)
        EditMessageCommand.register(this)
        CreateAttachmentsTableCommand.register(this)
        CopyTableCommand.register(this)
        CreateMessagesTableCommand.register(this)
        UploadAttachmentCommand.register(this)
        ToDBCommand.register(this)
        LinkGitHubCommand.register(this)
        UnlinkGitHubCommand.register(this)
        CheckGitHubCommand.register(this)
        ChatGPTCommand.register(this)
    }

    client.on<ApplicationCommandInteractionCreateEvent> {
        if (interaction.user.isBot) return@on
        if (interaction.invokedCommandName == "stats") StatsCommand.handle(interaction)
        if (interaction.invokedCommandName == "youtube") YouTubeCommand.handle(interaction)
        if (interaction.invokedCommandName == "vote") VoteCommand.handle(interaction)
        if (interaction.invokedCommandName == "translate-romaji") TranslateRomajiCommand.handle(interaction)
        if (interaction.invokedCommandName == "countrolemembers") CountRoleMembersCommand.handle(interaction)
        if (interaction.invokedCommandName == "permissions") PermissionsCommand.handle(interaction)
        if (interaction.invokedCommandName == "build") BuildCommand.handle(interaction)
        if (interaction.invokedCommandName == "custom-build") CustomBuildCommand.handle(interaction)
        if (interaction.invokedCommandName == "add-roles") AddRolesCommand.handle(interaction)
        if (interaction.invokedCommandName == "create-message") CreateMessageCommand.handle(interaction)
        if (interaction.invokedCommandName == "edit-message") EditMessageCommand.handle(interaction)
        if (interaction.invokedCommandName == "create-attachments-table") CreateAttachmentsTableCommand.handle(interaction)
        if (interaction.invokedCommandName == "copy-table") CopyTableCommand.handle(interaction)
        if (interaction.invokedCommandName == "create-messages-table") CreateMessagesTableCommand.handle(interaction)
        if (interaction.invokedCommandName == "upload-attachment") UploadAttachmentCommand.handle(interaction)
        if (interaction.invokedCommandName == "to-db") ToDBCommand.handle(interaction)
        if (interaction.invokedCommandName == "link-github") LinkGitHubCommand.handle(interaction)
        if (interaction.invokedCommandName == "unlink-github") UnlinkGitHubCommand.handle(interaction)
        if (interaction.invokedCommandName == "check-github") CheckGitHubCommand.handle(interaction)
        if (interaction.invokedCommandName == "chatgpt") ChatGPTCommand.handle(interaction)
    }

    client.on<MessageCreateEvent> {
        val handler = messageHandlers.findLast { it.canProcess(message) } ?: return@on
        handler.handle(message)
    }

    client.on<MemberJoinEvent> {
        val id = System.getenv("WELCOME_CHANNEL_ID")
        if (id.isNullOrBlank()) return@on
        if (member.isBot) return@on
        val channel = client.getChannel(Snowflake(id)) ?: return@on
        if (channel !is TextChannel) return@on
        if (channel.guildId != member.guildId) return@on
        channel.createMessage("""
            |${member.mention}
            |__新人運営のやることリスト__
            |・規約を確認して同意する https://www.azisaba.net/operating-terms-and-conditions/
            |・<#${Constant.SELF_INTRO_CHANNEL}>でPIN留めされたテンプレート通りに自己紹介を行う。
            |・ディスコードのニックネームにMCIDを明記する（形式はほかの運営を参考に）
            |
            |続いて、参加していない場合は以下のグループに参加してください。
            |
        """.trimMargin() + System.getenv("INVITE_LINKS"))
    }

    client.on<ReadyEvent> {
        println("Logged in as ${kord.getSelf().tag}!")
        guilds.forEach { guild -> guild.requestMembers() }
        println("Fetched all members in ${this.guilds.size} guilds.")
    }

    client.on<MemberLeaveEvent> {
        val channel = client.getChannel(Constant.LEAVE_LOG_CHANNEL) as? TextChannel
        if (channel == null) {
            println("Warning: Tried to get text channel ${Constant.LEAVE_LOG_CHANNEL} but the channel does not exist or is not a text channel.")
            return@on
        }
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
        channel.createMessage("""
            :outbox_tray: `${user.tag}` <@${user.id}> (ID: ${user.id}, Is bot: ${user.isBot})
            GitHub: `${currentGitHubConnection}`
            
            Nickname:
            ```
            ${old?.nickname}
            ```
            
            Roles:
            ```
            ${old?.roles?.toList()?.joinToString(", ") { it.name }}
            
            (IDs: ${old?.roleIds?.joinToString(", ")})
            ```
            
            Joined at: <t:${old?.joinedAt?.epochSeconds}> <t:${old?.joinedAt?.epochSeconds}:R>
            """.trimIndent())
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
