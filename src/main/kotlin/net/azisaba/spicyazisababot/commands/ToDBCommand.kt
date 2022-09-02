package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.MessageType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optSnowflake
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.Util.validateTable
import org.mariadb.jdbc.MariaDbBlob
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object ToDBCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val table = interaction.optString("table")!!
        if (!table.validateTable()) {
            interaction.respondEphemeral { content = "Invalid table name" }
            return
        }
        val channelId = interaction.optSnowflake("channel")!!
        val channel = interaction.channel.getGuildOrNull()!!.getChannel(channelId)
        if (channel !is TopGuildMessageChannel && channel !is ThreadChannel) {
            error("unsupported channel type: ${channel.type}")
        }
        channel as GuildMessageChannel
        val topGuildChannel = if (channel is ThreadChannel) channel.getParent() else channel as TopGuildMessageChannel
        if (!topGuildChannel.getEffectivePermissions(interaction.user.id)
                .contains(Permissions(Permission.ViewChannel, Permission.ReadMessageHistory))) {
            interaction.respondEphemeral {
                content = "You don't have permission to read message history in that channel."
            }
            return
        }
        val msg = interaction.respondPublic {
            content = "メッセージをデータベースにコピー中..."
        }
        val startedAt = Instant.now().epochSecond
        var lastEditMessageAttempt = 0L
        var collectedMessagesCount = 0L
        var skippedFiles = 0L
        var fetchedFiles = 0L
        val guildId = channel.guildId.toString()
        val guildName = channel.getGuild().name
        val channelIdString = channel.id.toString()
        val channelName = channel.name
        val connection = Util.getConnection()
        channel.messages.collect { collectedMessage ->
            collectedMessagesCount++
            if (System.currentTimeMillis() - lastEditMessageAttempt > 5000) {
                msg.edit {
                    content = """
                        メッセージをデータベースにコピー中...
                        経過時間: ${Instant.now().epochSecond - startedAt}秒
                        メッセージ数: $collectedMessagesCount
                        取得したファイル数: $fetchedFiles
                        スキップしたファイル数: $skippedFiles
                    """.trimIndent()
                }
                lastEditMessageAttempt = System.currentTimeMillis()
            }
            val statement = connection.prepareStatement("INSERT INTO `$table` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
                if (attachment.size > 50000000) {
                    skippedFiles++
                    return@forEach // skip > 50 MB files
                }
                fetchedFiles++
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
                conn.getInputStream().use { input ->
                    attachmentStatement.setBlob(6, MariaDbBlob(input.readBytes()))
                }
                if (conn is HttpURLConnection) conn.disconnect()
                attachmentStatement.executeUpdate()
                attachmentStatement.close()
            }
        }
        msg.edit {
            content = """
                コピーが完了しました。
                かかった時間: ${Instant.now().epochSecond - startedAt}秒
                メッセージ数: $collectedMessagesCount
                取得したファイル数: $fetchedFiles
                スキップしたファイル数: $skippedFiles
            """.trimIndent()
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("to-db", "Copy messages to the database") {
            description(Locale.JAPANESE, "メッセージをデータベースにコピー")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            channel("channel", "The channel to copy messages from") {
                description(Locale.JAPANESE, "メッセージのコピー元のチャンネル")

                required = true
                channelTypes = CreateMessageCommand.textChannelTypes
            }
            string("table", "The table to copy messages to") {
                description(Locale.JAPANESE, "メッセージのコピー先のテーブル")

                required = true
            }
        }
    }
}
