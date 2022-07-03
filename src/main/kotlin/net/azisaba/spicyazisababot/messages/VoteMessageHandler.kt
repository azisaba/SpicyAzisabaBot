package net.azisaba.spicyazisababot.messages

import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Message

object VoteMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.content == "/vote"

    override suspend fun handle(message: Message) {
        message.channel.createEmbed {
            title = "投票URL"
            url = "https://www.azisaba.net/"
            description = "毎日しよう!!!!"
            field("Japan Minecraft Servers") {
                "https://minecraft.jp/servers/azisaba.net"
            }
            field("monocraft") {
                "https://monocraft.net/servers/xWBVrf1nqB2P0LxlMm2v"
            }
        }
    }
}
