package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.attachment
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.azisaba.spicyazisababot.util.Constant
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optAttachments
import net.azisaba.spicyazisababot.util.Util.optString
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.mariadb.jdbc.MariaDbBlob
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.GZIPOutputStream

object UploadAttachmentCommand : CommandHandler {
    private val REGEX = "^https://(?:media|cdn)\\.discord(?:app)?\\.com/attachments/\\d+/(\\d+)/(.*)\$".toRegex()
    private const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val algorithm = interaction.optString("algorithm") ?: "bz2"
        val url = interaction.optString("url")
        if (url != null) {
            if (!url.matches(REGEX)) {
                interaction.respondEphemeral { content = "`$url`はDiscordのURLではありません。" }
                return
            }
            val attachmentData = Attachment.parse(url)
            uploadAttachment(interaction, algorithm, attachmentData)
        }
        val attachment = interaction.optAttachments().getOrNull(0)
        if (attachment != null) {
            /*
            if (attachment.size > MAX_FILE_SIZE) {
                interaction.respondEphemeral { content = "ファイルの大きさは最大100MBです。" }
                return
            }
            */
            val attachmentData = Attachment(attachment.id.value.toLong(), attachment.url, attachment.filename)
            uploadAttachment(interaction, algorithm, attachmentData)
        }
    }

    private suspend fun uploadAttachment(interaction: ApplicationCommandInteraction, algorithm: String, attachmentData: Attachment) {
        val createdTime = Instant.now().epochSecond
        val msg = interaction.respondPublic {
            content = "ファイルをアップロード中..."
        }
        val connection = Util.getConnection()
        val deleteStatement = connection.prepareStatement("DELETE FROM `attachments` WHERE `attachment_id` = ?")
        deleteStatement.setString(1, attachmentData.id.toString())
        deleteStatement.executeUpdate()
        deleteStatement.close()
        connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)").use prep@ { attachmentStatement ->
            val newUrl = if (attachmentData.filename.endsWith(".$algorithm")) {
                attachmentData.url
            } else {
                attachmentData.url + ".$algorithm"
            }
            val newFilename = if (attachmentData.filename.endsWith(".$algorithm")) {
                attachmentData.filename
            } else {
                attachmentData.filename + ".$algorithm"
            }
            attachmentStatement.setString(1, "0")
            attachmentStatement.setString(2, attachmentData.id.toString())
            attachmentStatement.setString(3, newUrl)
            attachmentStatement.setString(4, newUrl)
            attachmentStatement.setBoolean(5, attachmentData.filename.startsWith("SPOILER_"))
            withContext(Dispatchers.IO) {
                val conn = URL(attachmentData.url).openConnection()
                conn.setRequestProperty("User-Agent", "SpicyAzisabaBot/main https://github.com/azisaba/SpicyAzisabaBot")
                conn.connect()
                if (conn is HttpURLConnection && conn.responseCode != 200) {
                    error("Unexpected response code: ${conn.responseCode} (${conn.responseMessage})")
                }
                conn.getInputStream().use { input ->
                    if (attachmentData.filename.endsWith(".$algorithm")) {
                        if (input.available() > MAX_FILE_SIZE) {
                            msg.edit { content = "保存できるファイルの大きさは最大100MBです。" }
                            return@use
                        }
                        attachmentStatement.setBlob(6, MariaDbBlob(input.readBytes()))
                    } else {
                        ByteArrayOutputStream().use { outBytes ->
                            if (algorithm == "bz2") {
                                BZip2CompressorOutputStream(outBytes).use { out ->
                                    input.transferTo(out)
                                }
                            } else if (algorithm == "gz") {
                                GZIPOutputStream(outBytes).use { out ->
                                    input.transferTo(out)
                                }
                            }
                            val newBytes = outBytes.toByteArray()
                            if (newBytes.size > MAX_FILE_SIZE) {
                                msg.edit {
                                    content = "保存できるファイルの大きさは最大100MBです。"
                                }
                                error("File too large! (gzipped: ${newBytes.size}, max: $MAX_FILE_SIZE)")
                            }
                            attachmentStatement.setBlob(6, MariaDbBlob(newBytes))
                        }
                    }
                }
                if (conn is HttpURLConnection) conn.disconnect()
                attachmentStatement.executeUpdate()
                attachmentStatement.close()
                msg.edit {
                    content = """
                    処理が完了しました。
                    かかった時間: ${Instant.now().epochSecond - createdTime}秒
                    ${Constant.MESSAGE_VIEWER_BASE_URL}/attachments/${attachmentData.id}/$newFilename?decompress=true
                """.trimIndent()
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("upload-attachment", "Upload an attachment") {
            string("url", "URL of the attachment") {
                this.minLength = 10
                this.maxLength = 6000
            }
            attachment("attachment", "Attachment to upload")
            string("algorithm", "Algorithm to use when compressing files (Default: bzip2)") {
                choice("bzip2", "bz2")
                choice("gzip", "gz")
            }
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
