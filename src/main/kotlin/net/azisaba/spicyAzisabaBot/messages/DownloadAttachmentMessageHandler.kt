package net.azisaba.spicyAzisabaBot.messages

import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import net.azisaba.spicyAzisabaBot.util.Util
import org.mariadb.jdbc.MariaDbBlob
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

@Suppress("SqlNoDataSourceInspection", "SqlResolve")
object DownloadAttachmentMessageHandler: MessageHandler {
    private val REGEX = "^https://(?:media|cdn)\\.discord(?:app)?\\.com/attachments/\\d+/(\\d+)/(.*)\$".toRegex()

    override suspend fun canProcess(message: Message): Boolean =
        message.author?.isBot != true &&
                (message.content.split(" ")[0] == "/download-attachment" ||
                        message.content.split(" ")[0] == "/download-attachments")

    override suspend fun handle(message: Message) {
        val args = message.content.split(" ").drop(1)
        if (message.content == "/download-attachment") {
            message.reply { content = "`/download-attachment <URL...>`" }
            return
        }
        args.forEach {
            if (!it.matches(REGEX)) {
                message.reply { content = "`${it}`はDiscordのURLではありません。" }
                return
            }
        }
        val attachments = args.map { Attachment.parse(it) }
        val msg = message.reply {
            content = "ファイルをダウンロード中..."
        }
        var lastEditMessageAttempt = 0L
        var fetchedFiles = 0L
        val connection = Util.getConnection()
        attachments.forEach { attachment ->
            if (System.currentTimeMillis() - lastEditMessageAttempt > 5000) {
                msg.edit {
                    content = """
                        メッセージをデータベースにコピー中...
                        経過時間: ${Instant.now().epochSecond - msg.timestamp.epochSeconds}秒
                        取得したファイル数: $fetchedFiles
                    """.trimIndent()
                }
                lastEditMessageAttempt = System.currentTimeMillis()
            }
            fetchedFiles++
            val deleteStatement = connection.prepareStatement("DELETE FROM `attachments` WHERE `attachment_id` = ?")
            deleteStatement.setString(1, attachment.id.toString())
            deleteStatement.executeUpdate()
            deleteStatement.close()
            val attachmentStatement = connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)")
            attachmentStatement.setString(1, "0")
            attachmentStatement.setString(2, attachment.id.toString())
            attachmentStatement.setString(3, attachment.url)
            attachmentStatement.setString(4, attachment.url)
            attachmentStatement.setBoolean(5, attachment.filename.startsWith("SPOILER_"))
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
        msg.edit {
            content = """
                処理が完了しました。
                かかった時間: ${Instant.now().epochSecond - msg.timestamp.epochSeconds}秒
                取得したファイル数: $fetchedFiles
            """.trimIndent()
        }
    }

    data class Attachment(val id: Long, val url: String, val filename: String) {
        companion object {
            fun parse(s: String): Attachment {
                val groups = REGEX.matchEntire(s)?.groupValues ?: error("Invalid attachment string: $s")
                return Attachment(
                    id = groups[1].toLong(),
                    url = groups[0],
                    filename = groups[2],
                )
            }
        }
    }
}
