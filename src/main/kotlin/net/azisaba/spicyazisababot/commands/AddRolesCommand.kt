package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.role
import net.azisaba.spicyazisababot.util.Util.optSnowflake

object AddRolesCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val fromRole = interaction.optSnowflake("from")!!
        val toRole = interaction.optSnowflake("to")!!
        interaction.channel.getGuildOrNull()!!.requestMembers().collect { event ->
            event.members
                .filter { member -> member.roleIds.contains(fromRole) }
                .filter { member -> !member.roleIds.contains(toRole) }
                .forEach { member -> member.addRole(toRole) }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("add-roles", "Add roles to members who has the role") {
            dmPermission = false
            defaultMemberPermissions = Permissions(Permission.ManageRoles.code)
            role("from", "Members to add roles to") {
                required = true
            }
            role("to", "Role to add") {
                required = true
            }
        }
    }
}
