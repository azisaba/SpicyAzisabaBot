package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import net.azisaba.spicyazisababot.util.Util.optString
import java.io.ByteArrayInputStream

object PeekConversationCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val id = interaction.optString("id")!!
        val snowflake = Snowflake(id.toULong())
        val data = ChatGPTCommand.conversations[snowflake]
        if (data == null) {
            interaction.respondEphemeral { content = "会話が見つかりません。" }
            return
        }
        var talk = "Model: ${data.model}\n"
        data.conversation.forEach { content ->
            talk += "${content.role}:\n"
            talk += "${content.content}\n\n"
        }
        interaction.respondEphemeral { addFile("conversation.txt", ChannelProvider { ByteArrayInputStream(talk.toByteArray()).toByteReadChannel() }) }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("peek-conversation", "Peek conversation") {
            string("id", "Snowflake") {
                required = true
                minLength = 8
            }
        }
    }
}
