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

object CopyTableCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val from = interaction.optString("from")!!
        val to = interaction.optString("to")!!
        if (!from.validateTable() || !to.validateTable()) {
            interaction.respondEphemeral { content = "Invalid table name" }
            return
        }
        val message = interaction.respondEphemeral {
            content = "コピー中...(`$from` -> `$to`)"
        }
        Util.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM $from").use { rs ->
                    while (rs.next()) {
                        connection.prepareStatement("INSERT INTO `$to` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                            .use { stmt ->
                                stmt.setString(1, rs.getString("guild_id"))
                                stmt.setString(2, rs.getString("guild_name"))
                                stmt.setString(3, rs.getString("channel_id"))
                                stmt.setString(4, rs.getString("channel_name"))
                                stmt.setString(5, rs.getString("author_is_bot"))
                                stmt.setString(6, rs.getString("author_id"))
                                stmt.setString(7, rs.getString("author_name"))
                                stmt.setString(8, rs.getString("author_discriminator"))
                                stmt.setString(9, rs.getString("message_id"))
                                stmt.setString(10, rs.getString("content"))
                                stmt.setBoolean(11, rs.getBoolean("edited"))
                                stmt.setLong(12, rs.getLong("edited_timestamp"))
                                stmt.setLong(13, rs.getLong("created_timestamp"))
                                stmt.setBoolean(14, rs.getBoolean("is_reply"))
                                stmt.setString(15, rs.getString("reply_to"))
                                stmt.executeUpdate()
                            }
                    }
                }
            }
        }
        message.edit { content = "テーブル`$from` -> `$to`にコピーしました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("copy-table", "Copy messages table to another") {
            description(Locale.JAPANESE, "メッセージのテーブルをコピー")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            string("from", "The table to copy from") {
                description(Locale.JAPANESE, "コピー元のテーブル")

                required = true
            }
            string("to", "The table to copy to") {
                description(Locale.JAPANESE, "コピー先のテーブル")

                required = true
            }
        }
    }
}
