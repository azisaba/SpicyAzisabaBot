package net.azisaba.spicyazisababot.config.command

import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("text")
@Serializable
data class CustomCommandResponseText(
    val content: String
) : CustomCommandConfig, CustomCommandResponse {
    override suspend fun execute(
        interaction: ApplicationCommandInteraction,
        defer: DeferredMessageInteractionResponseBehavior
    ) {
        respond(defer)
    }

    override suspend fun respond(defer: DeferredMessageInteractionResponseBehavior) {
        defer.respond { this.content = this@CustomCommandResponseText.content }
    }
}
