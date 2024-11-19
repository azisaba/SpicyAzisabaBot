package net.azisaba.spicyazisababot.config.command

import dev.kord.common.Color
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("embed")
@Serializable
data class CustomCommandResponseEmbed(
    val title: String? = null,
    val description: String? = null,
    val author: Author? = null,
    val url: String? = null,
    val color: Color? = null,
    val fields: List<Field> = listOf(),
) : CustomCommandConfig, CustomCommandResponse {
    override suspend fun execute(
        interaction: ApplicationCommandInteraction,
        defer: DeferredMessageInteractionResponseBehavior
    ) {
        respond(defer)
    }

    override suspend fun respond(defer: DeferredMessageInteractionResponseBehavior) {
        defer.respond {
            embed {
                this.author = this@CustomCommandResponseEmbed.author?.toKord()
                this.url = this@CustomCommandResponseEmbed.url
                this.description = this@CustomCommandResponseEmbed.description
                this.color = this@CustomCommandResponseEmbed.color
                this.title = this@CustomCommandResponseEmbed.title
                this.fields = this@CustomCommandResponseEmbed.fields.map { it.toKord() }.toMutableList()
            }
        }
    }

    @Serializable
    data class Author(val name: String? = null, val url: String? = null, val icon: String? = null) {
        fun toKord() = EmbedBuilder.Author().apply {
            this.name = this@Author.name
            this.url = this@Author.url
            this.icon = this@Author.icon
        }
    }

    @Serializable
    data class Field(val name: String, val value: String, val inline: Boolean? = null) {
        fun toKord() = EmbedBuilder.Field().apply {
            this.name = this@Field.name
            this.value = this@Field.value
            this.inline = this@Field.inline
        }
    }
}
