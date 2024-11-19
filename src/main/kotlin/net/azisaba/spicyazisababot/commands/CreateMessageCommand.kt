package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.channel
import net.azisaba.spicyazisababot.util.Util.modal
import net.azisaba.spicyazisababot.util.Util.optSnowflake

object CreateMessageCommand : CommandHandler {
    val textChannelTypes = listOf(
        ChannelType.GuildText,
        ChannelType.GuildNews,
        ChannelType.GuildVoice,
        ChannelType.PublicNewsThread,
        ChannelType.PrivateThread,
        ChannelType.PublicGuildThread,
    )

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val channelId = interaction.optSnowflake("channel")!!
        val channel = interaction.channel.getGuildOrNull()!!.getChannel(channelId)
        if (channel !is TopGuildMessageChannel && channel !is ThreadChannel) {
            error("unsupported channel type: ${channel.type}")
        }
        val topGuildChannel = if (channel is ThreadChannel) channel.getParent() else channel as TopGuildMessageChannel
        if (!topGuildChannel.getEffectivePermissions(interaction.user.id)
                .contains(Permissions(Permission.ViewChannel, Permission.SendMessages, Permission.ManageMessages))) {
            interaction.respondEphemeral {
                content = "You don't have permission to manage messages in that channel."
            }
            return
        }
        interaction.modal("Create message", {
            actionRow {
                textInput(TextInputStyle.Paragraph, "message", "Message")
            }
        }) {
            val content = this.textInputs["message"]!!.value!!
            if (content.isEmpty()) {
                this.respondEphemeral {
                    this.content = "Message is empty."
                }
            } else {
                (channel as MessageChannelBehavior).createMessage { this.content = content }
                this.respondEphemeral {
                    this.content = "Message sent."
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("create-message", "Send a message to the channel") {
            description(Locale.JAPANESE, "チャンネルにメッセージを送信")

            dmPermission = false
            defaultMemberPermissions = Permissions.Builder(Permission.ViewChannel.code + Permission.SendMessages.code + Permission.ManageMessages.code).build()

            channel("channel", "The channel to send the message to") {
                description(Locale.JAPANESE, "メッセージを送信するチャンネル")

                required = true
                channelTypes = textChannelTypes
            }
        }
    }
}
