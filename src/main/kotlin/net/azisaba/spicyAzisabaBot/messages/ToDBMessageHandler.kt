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
import java.time.Instant

@Suppress("SqlNoDataSourceInspection")
object ToDBMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/to-db"

    override suspend fun handle(message: Message) {
        // return if:
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true && // author does not have administrator (false or null)
            message.getAuthorAsMember()?.roleIds?.contains(Constant.DEVELOPER_ROLE) != true) { // and author does not have developer role (false or null)
            return
        }
        val args = message.content.split("\n")
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
            val statement = connection.prepareStatement("INSERT INTO ? VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            val authorData = collectedMessage.data.author
            statement.setObject(1, guildId) // guild_id
            statement.setObject(2, guildName) // guild_name
            statement.setObject(3, channelIdString) // channel_id
            statement.setObject(4, channelName) // channel_name
            statement.setObject(5, authorData.bot.orElse(false)) // author_is_bot
            statement.setObject(6, authorData.id.toString()) // author_id
            statement.setObject(7, authorData.username) // author_name
            statement.setObject(8, authorData.discriminator) // author_discriminator
            statement.setObject(9, collectedMessage.content) // content
            statement.setBoolean(10, collectedMessage.editedTimestamp != null) // edited
            statement.setLong(11, collectedMessage.editedTimestamp?.toEpochMilliseconds() ?: 0) // edited_timestamp
            statement.setLong(12, collectedMessage.timestamp.toEpochMilliseconds()) // created_timestamp
            statement.setBoolean(13, collectedMessage.type == MessageType.Reply) // is_reply
            statement.setObject(14, collectedMessage.referencedMessage?.id?.toString()) // reply_to
            statement.executeUpdate()
        }
        msg.edit { content = "コピーが完了しました。\nかかった時間: ${Instant.now().epochSecond - msg.timestamp.epochSeconds}秒\nメッセージ数: $collectedMessagesCount" }
    }
}
