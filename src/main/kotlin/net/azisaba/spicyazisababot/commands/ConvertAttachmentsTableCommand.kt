package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.spicyazisababot.config.BotConfig
import net.azisaba.spicyazisababot.util.Util

object ConvertAttachmentsTableCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        interaction.respondEphemeral { content = "Queued conversion of attachments table, check details in console" }
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM `attachments`").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val messageId = rs.getString("message_id")
                        val attachmentId = rs.getString("attachment_id")
                        val url = rs.getString("url")
                        val fileName = url.substringAfterLast("/")
                        val data = rs.getBlob("data")
                        println("Converting attachment $messageId-$attachmentId-$fileName")
                        Util.uploadAttachment("$messageId-$attachmentId-$fileName", data.binaryStream)
                        connection.prepareStatement("UPDATE `attachments` SET `data` = NULL, `url` = ? WHERE `message_id` = ? AND `attachment_id` = ?").use { updateStmt ->
                            updateStmt.setString(1, "${BotConfig.config.attachmentsRootUrl}/$messageId-$attachmentId-$fileName")
                            updateStmt.setString(2, messageId)
                            updateStmt.setString(3, attachmentId)
                            updateStmt.executeUpdate()
                        }
                        println("Converted attachment $messageId-$attachmentId-$fileName")
                    }
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("convert-attachments-table", "Convert attachments table")
    }
}
