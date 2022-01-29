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
import net.azisaba.spicyAzisabaBot.messages.CVEMessageHandler
import net.azisaba.spicyAzisabaBot.messages.CreateMessageHandler
import net.azisaba.spicyAzisabaBot.messages.EditMessageHandler
import net.azisaba.spicyAzisabaBot.messages.StatsMessageHandler
import net.azisaba.spicyAzisabaBot.messages.VoteMessageHandler

private val messageHandlers = listOf(
    CVEMessageHandler,
    VoteMessageHandler,
    StatsMessageHandler,
    CreateMessageHandler,
    EditMessageHandler,
)

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    val client = Kord(System.getenv("BOT_TOKEN"))

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
        """.trimIndent())
    }

    client.on<ReadyEvent> {
        println("Logged in!")
    }

    client.login { this.intents = Intents(Intent.GuildMembers, Intent.GuildMessages) }
}
