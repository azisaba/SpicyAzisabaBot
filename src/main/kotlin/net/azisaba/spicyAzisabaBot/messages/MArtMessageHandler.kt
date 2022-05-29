package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.Color
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import net.azisaba.spicyAzisabaBot.util.Util.mentionsSelf

object MArtMessageHandler : MessageHandler {
    override suspend fun canProcess(message: Message): Boolean =
        message.getAuthorAsMember()?.isBot == false &&
                message.mentionsSelf() &&
                (message.content.contains("おえかき") || message.content.contains("お絵描き"))

    override suspend fun handle(message: Message) {
        val channelId = message.getAuthorAsMember()!!.getVoiceStateOrNull()?.channelId
            ?: return YouTubeMessageHandler.sendNotInVCError(message)
        val code = try {
            YouTubeMessageHandler.createInvite(channelId, "902271654783242291") // sketchheads
        } catch (e: Exception) {
            message.channel.createEmbed {
                color = Color(0xFF0000)
                title = "Error"
                description = e.message
            }
            return
        }
        message.channel.createMessage {
            content = "https://discord.gg/$code"
        }
    }
}