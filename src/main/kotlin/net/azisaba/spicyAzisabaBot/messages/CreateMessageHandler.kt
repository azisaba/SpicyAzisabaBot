package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel

object CreateMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/create"

    override suspend fun handle(message: Message) {
        if (message.author?.isBot != false) return
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.ManageMessages) != true) {
            return
        }
        if (message.content == "/create") {
            message.reply { content = "`/create チャンネルID(改行)内容`" }
            return
        }
        val channelId = message.content.split("[\n ]".toRegex())[1].toLongOrNull()?.let { Snowflake(it) }
        if (channelId == null) {
            message.reply { content = "チャンネルIDか内容が指定されていません" }
            return
        }
        val msgContent = message.content.replaceFirst("^.*\n".toRegex(), "")
        if (msgContent.isBlank()) {
            message.reply { content = "内容が空です" }
            return
        }
        val channel = try {
            message.getGuild().getChannel(channelId)
        } catch (e: Exception) {
            message.reply { content = "チャンネルが見つかりません" }
            return
        }
        if (channel !is TextChannel) {
            message.reply { content = "指定されたチャンネルはテキストチャンネルではありません" }
            return
        }
        channel.createMessage { content = msgContent }
    }
}