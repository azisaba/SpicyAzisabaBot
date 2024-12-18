package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.role
import net.azisaba.spicyazisababot.util.Util.optSnowflake

object CountRoleMembersCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val role = interaction.optSnowflake("role")
        var count = 0
        interaction.channel.getGuildOrNull()!!.requestMembers().collect { event ->
            count += event.members.count { member -> member.roleIds.contains(role) }
        }
        interaction.respondEphemeral { content = "$count" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("countrolemembers", "Count members of roles") {
            description(Locale.JAPANESE, "ロールを持っているメンバーの数を表示")

            dmPermission = false
            defaultMemberPermissions = Permissions.Builder(Permission.ManageRoles.code).build()

            role("role", "The role") {
                name(Locale.JAPANESE, "ロール")
                description(Locale.JAPANESE, "ロール")

                required = true
            }
        }
    }
}
