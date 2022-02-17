package net.azisaba.spicyAzisabaBot.messages

import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import net.azisaba.spicyAzisabaBot.util.RomajiTextReader

object TranslateRomajiMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/translate-romaji"

    override suspend fun handle(message: Message) {
        if (message.content == "/translate-romaji") {
            message.reply { content = "`/translate-romaji 内容`" }
            return
        }
        val content = message.content.split(" ").drop(1).joinToString(" ")
        message.reply {
            this.content = RomajiTextReader.parse(content)
        }
    }
}
