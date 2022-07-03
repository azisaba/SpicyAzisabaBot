package net.azisaba.spicyazisababot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel

object CreateMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean =
        message.author?.isBot == false &&
                message.content.split(" ")[0] == "/create-message"

    override suspend fun handle(message: Message) {
        if (message.author?.isBot != false) return
        if (message.content == "/create-message") {
            message.reply { content = "`/create-message チャンネルID(改行)内容`" }
            return
        }
        val channelId = message.content.split("[\n ]".toRegex())[1].toLongOrNull()?.let { Snowflake(it) }
        if (channelId == null) {
            message.reply { content = "チャンネルIDか内容が指定されていません" }
            return
        }
        val msgContent = message.content.replaceFirst("^.*\n".toRegex(), "").replace("^```\n(.*)\n```".toRegex(), "$1")
        if (msgContent.isBlank()) {
            message.reply { content = "内容が空です" }
            return
        }
        val channel = try {
            message.kord.getChannel(channelId) ?: throw NullPointerException()
        } catch (e: Exception) {
            message.reply { content = "チャンネルが見つかりません" }
            return
        }
        if (channel !is TextChannel) {
            message.reply { content = "指定されたチャンネルはテキストチャンネルではありません" }
            return
        }
        if (channel.guild.getMemberOrNull(message.author!!.id)?.getPermissions()?.contains(Permission.ManageMessages) != true) {
            message.reply { content = "権限がありません。" }
            return
        }
        channel.createMessage { content = msgContent }
        message.reply {
            content = "メッセージを送信しました。\n```\n${msgContent}\n```"
        }
    }
}
