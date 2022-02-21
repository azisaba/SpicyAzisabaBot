package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import net.azisaba.spicyAzisabaBot.util.Constant
import net.azisaba.spicyAzisabaBot.util.Util

@Suppress("SqlNoDataSourceInspection", "SqlResolve")
object CreateAttachmentsTableMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/create-attachments-table"

    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true &&
            message.getAuthorAsMember()?.roleIds?.contains(Constant.DEVELOPER_ROLE) != true) {
            return
        }
        Util.getConnection().use {
            it.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE `attachments` (
                        `message_id` VARCHAR(255) NOT NULL DEFAULT "0",
                        `attachment_id` VARCHAR(255) NOT NULL DEFAULT "0",
                        `url` TEXT(65535) DEFAULT NULL,
                        `proxy_url` TEXT(65535) DEFAULT NULL,
                        `spoiler` BOOLEAN NOT NULL DEFAULT FALSE,
                        `data` LONGBLOB DEFAULT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        message.reply { content = "attachmentsテーブルを作成しました。" }
    }
}
