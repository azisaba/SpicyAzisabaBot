package net.azisaba.spicyAzisabaBot.messages

import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import net.azisaba.spicyAzisabaBot.util.Constant

object RealProblemChannelHandler : MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.channelId == Constant.REAL_PROBLEM_CHANNEL_ID

    override suspend fun handle(message: Message) {
        if (message.author?.isBot != false) return
        val content = message.content.lines().getOrNull(0) ?: return
        if (content.startsWith("^")) return
        (message.channel.fetchChannel() as TextChannel).startPublicThreadWithMessage(message.id, content)
    }
}
