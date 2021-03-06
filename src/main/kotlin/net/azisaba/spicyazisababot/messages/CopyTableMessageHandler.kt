package net.azisaba.spicyazisababot.messages

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import net.azisaba.spicyazisababot.util.Constant
import net.azisaba.spicyazisababot.util.Util

@Suppress("SqlNoDataSourceInspection")
object CopyTableMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.author?.isBot == false && message.content.split(" ")[0] == "/copy-table"

    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true &&
            message.getAuthorAsMember()?.roleIds?.contains(Constant.DEVELOPER_ROLE) != true) {
            return
        }
        val args = message.content.split(" ")
        if (message.content == "/copy-table" || args.size <= 2) {
            message.reply { content = "`/copy-table <from> <to>`" }
            return
        }
        Util.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM ${args[1]}").use { rs ->
                    while (rs.next()) {
                        connection.prepareStatement("INSERT INTO `${args[2]}` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
        message.reply { content = "????????????`${args[1]}` -> `${args[2]}`???????????????????????????" }
    }
}
