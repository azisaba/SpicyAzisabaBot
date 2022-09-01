package net.azisaba.spicyazisababot.messages

import dev.kord.common.Color
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.embed
import net.azisaba.spicyazisababot.commands.YouTubeCommand
import net.azisaba.spicyazisababot.util.Util.mentionsSelf

object MArtMessageHandler : MessageHandler {
    override suspend fun canProcess(message: Message): Boolean =
        message.getAuthorAsMember()?.isBot == false &&
                message.mentionsSelf() &&
                (message.content.contains("おえかき") || message.content.contains("お絵描き"))

    override suspend fun handle(message: Message) {
        val channelId = message.getAuthorAsMember()!!.getVoiceStateOrNull()?.channelId
            ?: return sendNotInVCError(message)
        val code = try {
            YouTubeCommand.createInvite(channelId, "902271654783242291") // sketchheads
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

    private suspend fun sendNotInVCError(message: Message) {
        message.reply {
            embed {
                color = Color(0xFF0000)
                title = "Error"
                description = "You are not in a voice channel. (Please rejoin the voice channel and try again)"
            }
        }
    }
}