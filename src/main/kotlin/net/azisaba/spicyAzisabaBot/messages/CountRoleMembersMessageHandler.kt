package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.reply
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.Message
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.collect

object CountRoleMembersMessageHandler : MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/countrolemembers"

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.ManageRoles) != true) {
            return
        }
        val args = message.content.split(" ")
        if (args.size == 1) {
            message.reply { content = "`/countrolemembers <role>`" }
            return
        }
        var count = 0
        val role = Snowflake(args[1].toLong())
        message.getGuild().requestMembers().collect { event ->
            count += event.members.count { member -> member.roleIds.contains(role) }
        }
        message.reply { content = "$count" }
    }
}
