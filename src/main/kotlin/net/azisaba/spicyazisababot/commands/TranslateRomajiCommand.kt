package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.spicyazisababot.util.RomajiTextReader
import net.azisaba.spicyazisababot.util.Util.modal

object TranslateRomajiCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        interaction.modal("Content", {
            this.actionRow {
                this.textInput(TextInputStyle.Paragraph, "content", "Content") {
                    required = true
                }
            }
        }) {
            val optContent = this.textInputs["content"]?.value ?: return@modal
            val content = RomajiTextReader.parse(optContent)
            respondEphemeral {
                this.content = content
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("translate-romaji", "Translate romaji to hiragana") {
            description(Locale.JAPANESE, "ローマ字をひらがなに変換")
        }
    }
}
