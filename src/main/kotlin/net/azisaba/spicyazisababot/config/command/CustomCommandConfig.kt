package net.azisaba.spicyazisababot.config.command

import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import kotlinx.serialization.Serializable

@Serializable
sealed interface CustomCommandConfig {
    suspend fun execute(interaction: ApplicationCommandInteraction, defer: DeferredMessageInteractionResponseBehavior)
}
