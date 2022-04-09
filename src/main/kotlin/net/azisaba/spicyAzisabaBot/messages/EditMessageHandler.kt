package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import util.ArgumentParser

object EditMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.author?.isBot != true &&
            message.content.split(" ")[0] == "/edit"

    override suspend fun handle(message: Message) {
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
        val editContent = message.content.replaceFirst("^.*\n".toRegex(), "").replace("^```\n(.*)\n```".toRegex(), "$1")
        if (editContent.isBlank()) {
            message.reply { content = "内容が空です" }
            return
        }
        val channel = try {
            message.kord.getChannel(channelId) ?: NullPointerException()
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
        val targetMessage = try {
            channel.getMessage(messageId)
        } catch (e: Exception) {
            message.reply { content = "メッセージが見つかりません" }
            return
        }
        targetMessage.edit { content = editContent }
        message.reply {
            content = "メッセージを送信しました。\n```\n${editContent}\n```"
        }
    }
}
