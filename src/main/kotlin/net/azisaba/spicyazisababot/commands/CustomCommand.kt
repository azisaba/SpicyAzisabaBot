package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.spicyazisababot.config.command.CustomCommandDefinition

class CustomCommand(private val definition: CustomCommandDefinition) : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer: DeferredMessageInteractionResponseBehavior = if (definition.responseType == CustomCommandDefinition.ResponseType.Public) {
            interaction.deferPublicResponse()
        } else {
            interaction.deferEphemeralResponse()
        }
        try {
            definition.config.execute(interaction, defer)
        } catch (e: Exception) {
            defer.respond { content = "エラーが発生しました: ${e.message}" }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input(definition.name, definition.description)
    }
}
