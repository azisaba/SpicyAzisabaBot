package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.create.embed

object VoteCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        interaction.respondEphemeral {
            embed {
                title = "投票URL"
                url = "https://www.azisaba.net/"
                description = "毎日しよう!!!!"
                field("Japan Minecraft Servers") {
                    "https://minecraft.jp/servers/azisaba.net"
                }
                field("monocraft") {
                    "https://monocraft.net/servers/xWBVrf1nqB2P0LxlMm2v"
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("vote", "pls vote")
    }
}
