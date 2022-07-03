package net.azisaba.spicyazisababot.messages

import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.Message
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList

object StatsMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.content == "/stats" && message.getGuildOrNull() != null

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle(message: Message) {
        message.channel.createEmbed {
            this.title = "/stats"
            this.field("サーバー人数") {
                var botCount = 0
                var nonBotCount = 0
                message.getGuild().requestMembers().collect {
                    it.members.forEach { member ->
                        if (member.isBot) {
                            botCount++
                        } else {
                            nonBotCount++
                        }
                    }
                }
                "合計 ${botCount + nonBotCount} / ボット $botCount / ユーザー $nonBotCount"
            }
            this.field("Features") { message.getGuild().features.joinToString(", ") { it.value } }
            this.field("サーバーブースト") { "レベル ${message.getGuild().premiumTier.value} (${message.getGuild().premiumSubscriptionCount ?: 1}ブースト)" }
            this.field("チャンネル数") {
                val channels = message.getGuild().channels.toList()
                val categories = channels.count { it.type == ChannelType.GuildCategory }
                val texts = channels.count { it.type == ChannelType.GuildText || it.type == ChannelType.GuildNews }
                val voices = channels.count { it.type == ChannelType.GuildVoice || it.type == ChannelType.GuildStageVoice }
                val ctv = categories + texts + voices
                "C+T+V $ctv / カテゴリ $categories / テキスト $texts / ボイス $voices"
            }
        }
    }
}
