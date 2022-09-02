package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.Util.validateTable

object CreateMessagesTableCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val name = interaction.optString("name")!!
        if (!name.validateTable()) {
            interaction.respondEphemeral { content = "Invalid table name" }
            return
        }
        val message = interaction.respondEphemeral {
            content = "テーブル`$name`を作成中..."
        }
        Util.getConnection().use {
            it.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE `$name` (
                        `guild_id` VARCHAR(255) NOT NULL DEFAULT "0", -- guild (server) id
                        `guild_name` VARCHAR(255) NOT NULL DEFAULT "0", -- guild (server) name
                        `channel_id` VARCHAR(255) NOT NULL DEFAULT "0", -- channel id
                        `channel_name` VARCHAR(255) NOT NULL DEFAULT "unknown", -- channel name
                        `author_is_bot` VARCHAR(255) NOT NULL DEFAULT "0", -- true if author is bot [5]
                        `author_id` VARCHAR(255) NOT NULL DEFAULT "0", -- user id of author
                        `author_name` VARCHAR(255) NOT NULL DEFAULT "Deleted User", -- "username"#0000
                        `author_discriminator` VARCHAR(16) NOT NULL DEFAULT "0000", -- username#"0000"
                        `message_id` VARCHAR(255) NOT NULL DEFAULT "0", -- message id
                        `content` TEXT(65535) NOT NULL DEFAULT "", -- message content [10]
                        `edited` BOOLEAN NOT NULL DEFAULT FALSE, -- true if edited at least once
                        `edited_timestamp` BIGINT(255) NOT NULL DEFAULT 0, -- timestamp when the message was last edited at
                        `created_timestamp` BIGINT(255) NOT NULL DEFAULT 0, -- timestamp when the message was created at
                        `is_reply` BOOLEAN NOT NULL DEFAULT FALSE, -- true if reply, false otherwise
                        `reply_to` VARCHAR(255) DEFAULT NULL -- referenced message id [15]
                    )
                    """.trimIndent()
                )
            }
        }
        message.edit { content = "テーブル`$name`を作成しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("create-messages-table", "Create messages tables") {
            description(Locale.JAPANESE, "メッセージのテーブルを作成")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            string("name", "Table name") {
                description(Locale.JAPANESE, "テーブルの名前")

                required = true
                minLength = 3
                maxLength = 50
            }
        }
    }
}
