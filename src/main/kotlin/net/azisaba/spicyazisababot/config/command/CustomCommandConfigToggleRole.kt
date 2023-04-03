package net.azisaba.spicyazisababot.config.command

import com.charleskorn.kaml.YamlComment
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.azisaba.spicyazisababot.commands.getGuildOrNull

@SerialName("toggle-role")
@Serializable
data class CustomCommandConfigToggleRole(
    @YamlComment("Map<サーバーID, ロールID>")
    val roleIds: Map<String, String>,
    val responseOffToOn: CustomCommandResponse,
    val responseOnToOff: CustomCommandResponse,
) : CustomCommandConfig {
    override suspend fun execute(
        interaction: ApplicationCommandInteraction,
        defer: DeferredMessageInteractionResponseBehavior
    ) {
        val guild = interaction.channel.getGuildOrNull() ?: run {
            defer.respond { content = "このコマンドはDMでは実行できません。" }
            return
        }
        val roleId = roleIds[guild.id.toString()] ?: run {
            defer.respond { content = "このサーバーではこのコマンドは使用できません。" }
            return
        }
        val member = guild.getMember(interaction.user.id)
        val roleResult = runCatching { guild.getRole(Snowflake(roleId)) }
        if (roleResult.isFailure) {
            defer.respond { content = "ロールが見つかりません。" }
            return
        }
        if (member.roleIds.contains(Snowflake(roleId))) {
            member.removeRole(Snowflake(roleId), "custom command issued by ${member.id}")
            responseOnToOff.respond(defer)
        } else {
            member.addRole(Snowflake(roleId), "custom command issued by ${member.id}")
            responseOffToOn.respond(defer)
        }
    }
}
