package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.spicyazisababot.util.Util

object CreateAttachmentsTableCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
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
        interaction.respondEphemeral { content = "attachmentsテーブルを作成しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("create-attachments-table", "Create attachments tables") {
            description(Locale.JAPANESE, "attachmentsテーブルを作成")

            dmPermission = false
            defaultMemberPermissions = Permissions()
        }
    }
}
