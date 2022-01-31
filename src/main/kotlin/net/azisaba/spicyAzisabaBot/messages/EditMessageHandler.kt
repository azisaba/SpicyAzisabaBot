package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import util.ArgumentParser

object EditMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/edit"

    override suspend fun handle(message: Message) {
        if (message.author?.isBot != false) return
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.ManageMessages) != true) {
            return
        }
        if (message.content == "/edit") {
            message.reply { content = "`/edit [channel=チャンネルID] <メッセージID>(改行)内容`" }
            return
        }
        val args = ArgumentParser(message.content.split("\n")[0])
        val channelId = args.parsedRawOptions["channel"]?.toLong()?.let { Snowflake(it) } ?: message.channelId
        val messageId = args.arguments.filter { !it.contains("=") }.getOrNull(1)?.toLong()?.let { Snowflake(it) }
        if (messageId == null) {
            message.reply { content = "メッセージIDが指定されていません" }
            return
        }
        val editContent = message.content.replaceFirst("^.*\n".toRegex(), "")
        if (editContent.isBlank()) {
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
        val targetMessage = try {
            channel.getMessage(messageId)
        } catch (e: Exception) {
            message.reply { content = "メッセージが見つかりません" }
            return
        }
        targetMessage.edit { content = editContent }
    }
}
