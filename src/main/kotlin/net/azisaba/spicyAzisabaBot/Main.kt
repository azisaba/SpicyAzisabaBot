@file:JvmName("MainKt")
package net.azisaba.spicyAzisabaBot

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import net.azisaba.spicyAzisabaBot.messages.AddRolesMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CVEMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CopyTableMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CountRoleMembersMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CreateAttachmentsTableMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CreateMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CreateTableMessageHandler
import net.azisaba.spicyAzisabaBot.messages.DownloadAttachmentMessageHandler
import net.azisaba.spicyAzisabaBot.messages.EditMessageHandler
import net.azisaba.spicyAzisabaBot.messages.RealProblemChannelHandler
import net.azisaba.spicyAzisabaBot.messages.StatsMessageHandler
import net.azisaba.spicyAzisabaBot.messages.ToDBMessageHandler
import net.azisaba.spicyAzisabaBot.messages.TranslateRomajiMessageHandler
import net.azisaba.spicyAzisabaBot.messages.VoteMessageHandler
import net.azisaba.spicyAzisabaBot.messages.YouTubeMessageHandler
import net.azisaba.spicyAzisabaBot.util.Util

private val messageHandlers = listOf(
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
            ${member.mention}
            __新人運営のやることリスト__
            ・規約を確認して同意する https://www.azisaba.net/operating-terms-and-conditions/
            ・ 自己紹介 でPIN留めされたテンプレート通りに自己紹介を行う。
            ・ディスコードのニックネームにMCIDを明記する（形式はほかの運営を参考に）
            
            続いて、参加していない場合は以下のグループに参加してください。
            ${System.getenv("INVITE_LINKS")}
        """.trimIndent())
    }

    client.on<ReadyEvent> {
        println("Logged in!")
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
