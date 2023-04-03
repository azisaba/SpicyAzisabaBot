package net.azisaba.spicyazisababot.config.command

import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import kotlinx.serialization.Serializable

@Serializable
sealed interface CustomCommandResponse {
    suspend fun respond(defer: DeferredMessageInteractionResponseBehavior)
}
