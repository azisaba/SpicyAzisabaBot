@file:JvmName("MainKt")
package net.azisaba.spicyazisababot

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.toList
import net.azisaba.spicyazisababot.messages.AddRolesMessageHandler
import net.azisaba.spicyazisababot.messages.BuildMessageHandler
import net.azisaba.spicyazisababot.messages.CVEMessageHandler
import net.azisaba.spicyazisababot.messages.CopyTableMessageHandler
import net.azisaba.spicyazisababot.messages.CountRoleMembersMessageHandler
import net.azisaba.spicyazisababot.messages.CreateAttachmentsTableMessageHandler
import net.azisaba.spicyazisababot.messages.CreateMessageHandler
import net.azisaba.spicyazisababot.messages.CreateTableMessageHandler
import net.azisaba.spicyazisababot.messages.DownloadAttachmentMessageHandler
import net.azisaba.spicyazisababot.messages.EditMessageHandler
import net.azisaba.spicyazisababot.messages.MArtMessageHandler
import net.azisaba.spicyazisababot.messages.RealProblemChannelHandler
import net.azisaba.spicyazisababot.messages.StatsMessageHandler
import net.azisaba.spicyazisababot.messages.ToDBMessageHandler
import net.azisaba.spicyazisababot.messages.TranslateRomajiMessageHandler
import net.azisaba.spicyazisababot.messages.VoteMessageHandler
import net.azisaba.spicyazisababot.messages.YouTubeMessageHandler
import net.azisaba.spicyazisababot.util.Constant
import net.azisaba.spicyazisababot.util.Util

private val messageHandlers = listOf(
    BuildMessageHandler,
    CVEMessageHandler,
    VoteMessageHandler,
    StatsMessageHandler,
    CreateMessageHandler,
    EditMessageHandler,
    RealProblemChannelHandler,
    AddRolesMessageHandler,
    CountRoleMembersMessageHandler,
    TranslateRomajiMessageHandler,
    CreateTableMessageHandler,
    CreateAttachmentsTableMessageHandler,
    CopyTableMessageHandler,
    ToDBMessageHandler,
    DownloadAttachmentMessageHandler,
    YouTubeMessageHandler,

    // triggered by mentions
    MArtMessageHandler,
)

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    val client = Kord(Util.getEnvOrThrow("BOT_TOKEN"))

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
        println("Logged in!")
    }

    client.on<MemberLeaveEvent> {
        val channel = client.getChannel(Constant.LEAVE_LOG_CHANNEL) as? TextChannel
        if (channel == null) {
            println("Warning: Tried to get text channel ${Constant.LEAVE_LOG_CHANNEL} but the channel does not exist or is not a text channel.")
            return@on
        }
        if (channel.guildId != guildId) return@on
        channel.createMessage("""
            :outbox_tray: `${user.tag}` <@${user.id}> (ID: ${user.id}, Is bot: ${user.isBot})
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
            Intent.GuildMembers,
            Intent.GuildMessages,
            Intent.DirectMessages,
            Intent.GuildVoiceStates,
            Intent.GuildPresences,
            Intent.MessageContent,
        )
    }
}
