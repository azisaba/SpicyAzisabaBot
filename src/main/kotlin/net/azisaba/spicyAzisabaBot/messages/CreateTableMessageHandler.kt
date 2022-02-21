package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import net.azisaba.spicyAzisabaBot.util.Constant
import net.azisaba.spicyAzisabaBot.util.Util

@Suppress("SqlNoDataSourceInspection")
object CreateTableMessageHandler: MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/create-table"

    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true &&
            message.getAuthorAsMember()?.roleIds?.contains(Constant.DEVELOPER_ROLE) != true) {
            return
        }
        val args = message.content.split(" ")
        if (message.content == "/create-table") {
            message.reply { content = "`/create-table <テーブル名>`" }
            return
        }
        Util.getConnection().use {
            val statement = it.prepareStatement("""
                CREATE TABLE ? (
                    `guild_id` VARCHAR(255) NOT NULL DEFAULT "0", -- guild (server) id
                    `guild_name` VARCHAR(255) NOT NULL DEFAULT "0", -- guild (server) name
                    `channel_id` VARCHAR(255) NOT NULL DEFAULT "0", -- channel id
                    `channel_name` VARCHAR(255) NOT NULL DEFAULT "unknown", -- channel name
                    `author_is_bot` VARCHAR(255) NOT NULL DEFAULT "0", -- user id of author [5]
                    `author_id` VARCHAR(255) NOT NULL DEFAULT "0", -- user id of author
                    `author_name` VARCHAR(255) NOT NULL DEFAULT "Deleted User", -- "username"#0000
                    `author_discriminator` VARCHAR(16) NOT NULL DEFAULT "0000", -- username#"0000"
                    `content` TEXT(65535) NOT NULL DEFAULT "", -- message content
                    `edited` BOOLEAN NOT NULL DEFAULT FALSE, -- true if edited at least once [10]
                    `edited_timestamp` BIGINT(255) NOT NULL DEFAULT 0, -- timestamp when the message was last edited at
                    `created_timestamp` BIGINT(255) NOT NULL DEFAULT 0, -- timestamp when the message was created at
                    `is_reply` BOOLEAN NOT NULL DEFAULT FALSE, -- true if reply, false otherwise
                    `reply_to` VARCHAR(255) DEFAULT NULL -- referenced message id [14]
                )
            """.trimIndent())
            statement.setObject(1, args[1])
            statement.executeUpdate()
        }
        message.reply { content = "テーブル`${args[1]}`を作成しました。" }
    }
}
