package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.reply
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.Message
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.flow.collect

object AddRolesMessageHandler : MessageHandler {
    override fun canProcess(message: Message): Boolean = message.author?.isBot != true && message.content.split(" ")[0] == "/addroles"

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.ManageRoles) != true) {
            return
        }
        val args = message.content.split(" ")
        if (args.size <= 2) {
            message.reply { content = "`/addroles <from role> <to role>`" }
            return
        }
        val fromRole = Snowflake(args[1].toLong())
        val toRole = Snowflake(args[2].toLong())
        message.getGuild().requestMembers().collect { event ->
            event.members
                .filter { member -> member.roleIds.contains(fromRole) }
                .forEach { member -> member.addRole(toRole) }
        }
    }
}
