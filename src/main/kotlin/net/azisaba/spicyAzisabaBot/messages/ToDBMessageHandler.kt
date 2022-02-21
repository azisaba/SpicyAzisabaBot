package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.MessageType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.collect
import net.azisaba.spicyAzisabaBot.util.Constant
import net.azisaba.spicyAzisabaBot.util.Util
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

@Suppress("SqlNoDataSourceInspection", "SqlResolve")
object ToDBMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/to-db"

    override suspend fun handle(message: Message) {
        // return if:
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true && // author does not have administrator (false or null)
            message.getAuthorAsMember()?.roleIds?.contains(Constant.DEVELOPER_ROLE) != true) { // and author does not have developer role (false or null)
            return
        }
        val args = message.content.split(" ")
        if (message.content == "/to-db" || args.size <= 2) {
            message.reply { content = "`/to-db <チャンネルID> <テーブル名>`" }
            return
        }
        val channelId = args[1].toLongOrNull()?.let { Snowflake(it) }
            ?: return message.reply { content = "チャンネルが見つかりません" }.let {}
        val channel = try {
            message.getGuild().getChannel(channelId) as? TextChannel
                ?: return message.reply { content = "チャンネルが見つかりません" }.let {}
        } catch (e: Exception) {
            message.reply { content = "チャンネルが見つかりません" }
            return
        }
        val msg = message.reply {
            content = "メッセージをデータベースにコピー中..."
        }
        var lastEditMessageAttempt = 0L
        var collectedMessagesCount = 0L
        val guildId = channel.guildId.toString()
        val guildName = channel.getGuild().name
        val channelIdString = channelId.toString()
        val channelName = channel.name
        val connection = Util.getConnection()
        channel.messages.collect { collectedMessage ->
            collectedMessagesCount++
            if (System.currentTimeMillis() - lastEditMessageAttempt > 5000) {
                msg.edit { content = "メッセージをデータベースにコピー中...\n経過時間: ${Instant.now().epochSecond - msg.timestamp.epochSeconds}秒\nメッセージ数: $collectedMessagesCount" }
                lastEditMessageAttempt = System.currentTimeMillis()
            }
            val statement = connection.prepareStatement("INSERT INTO `${args[2]}` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            val authorData = collectedMessage.data.author
            statement.setObject(1, guildId) // guild_id
            statement.setObject(2, guildName) // guild_name
            statement.setObject(3, channelIdString) // channel_id
            statement.setObject(4, channelName) // channel_name
            statement.setObject(5, authorData.bot.orElse(false)) // author_is_bot
            statement.setObject(6, authorData.id.toString()) // author_id
            statement.setObject(7, authorData.username) // author_name
            statement.setObject(8, authorData.discriminator) // author_discriminator
            statement.setObject(9, collectedMessage.id.toString())
            statement.setObject(10, collectedMessage.content) // content
            statement.setBoolean(11, collectedMessage.editedTimestamp != null) // edited
            statement.setLong(12, collectedMessage.editedTimestamp?.toEpochMilliseconds() ?: 0) // edited_timestamp
            statement.setLong(13, collectedMessage.timestamp.toEpochMilliseconds()) // created_timestamp
            statement.setBoolean(14, collectedMessage.type == MessageType.Reply) // is_reply
            statement.setObject(15, collectedMessage.referencedMessage?.id?.toString()) // reply_to
            statement.executeUpdate()
            statement.close()
            collectedMessage.attachments.forEach { attachment ->
                if (attachment.size > 50000000) return@forEach // skip > 50 MB files
                val deleteStatement = connection.prepareStatement("DELETE FROM `attachments` WHERE `attachment_id` = ?")
                deleteStatement.setObject(1, attachment.id.toString())
                deleteStatement.executeUpdate()
                deleteStatement.close()
                val attachmentStatement = connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)")
                attachmentStatement.setObject(1, collectedMessage.id.toString())
                attachmentStatement.setObject(2, attachment.id.toString())
                attachmentStatement.setObject(3, attachment.url)
                attachmentStatement.setObject(4, attachment.proxyUrl)
                attachmentStatement.setObject(5, attachment.isSpoiler)
                val conn = URL(attachment.url).openConnection()
                conn.setRequestProperty("User-Agent", "SpicyAzisabaBot/main https://github.com/azisaba/SpicyAzisabaBot")
                conn.connect()
                if (conn is HttpURLConnection && conn.responseCode != 200) {
                    error("Unexpected response code: ${conn.responseCode} (${conn.responseMessage})")
                }
                conn.getInputStream().use { input -> attachmentStatement.setBlob(6, input) }
                if (conn is HttpURLConnection) conn.disconnect()
                attachmentStatement.executeUpdate()
                attachmentStatement.close()
            }
        }
        msg.edit { content = "コピーが完了しました。\nかかった時間: ${Instant.now().epochSecond - msg.timestamp.epochSeconds}秒\nメッセージ数: $collectedMessagesCount" }
    }
}
