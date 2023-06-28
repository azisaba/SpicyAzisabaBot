package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.exception.EntityNotFoundException
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import net.azisaba.spicyazisababot.util.Util.modal
import net.azisaba.spicyazisababot.util.Util.optSnowflake
import net.azisaba.spicyazisababot.util.Util.optString

object EditMessageCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val channelId = interaction.optSnowflake("channel")!!
        val messageId = Snowflake(interaction.optString("message")!!)
        val channel = interaction.channel.getGuildOrNull()!!.getChannel(channelId)
        if (channel !is TopGuildMessageChannel) {
            error("unsupported channel type: ${channel.type}")
        }
        if (!channel.getEffectivePermissions(interaction.user.id)
                .contains(Permissions(Permission.ViewChannel, Permission.ReadMessageHistory, Permission.ManageMessages))) {
            interaction.respondEphemeral {
                content = "You don't have permission to manage messages in that channel."
            }
            return
        }
        val message = try {
            channel.getMessage(messageId)
        } catch (e: EntityNotFoundException) {
            interaction.respondEphemeral {
                content = "Message not found."
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
                interaction.respondEphemeral {
                    this.content = "Message is empty."
                }
            } else {
                message.edit { this.content = content }
                interaction.respondEphemeral {
                    this.content = "Edited the message."
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("edit-message", "Edit a message") {
            description(Locale.JAPANESE, "メッセージを編集")

            dmPermission = false
            defaultMemberPermissions = Permissions(Permission.ViewChannel.code + Permission.ReadMessageHistory.code + Permission.ManageMessages.code)

            channel("channel", "The channel where the message is located") {
                description(Locale.JAPANESE, "メッセージがあるチャンネル")

                required = true
                channelTypes = CreateMessageCommand.textChannelTypes
            }
            string("message", "The message ID") {
                description(Locale.JAPANESE, "メッセージID")
                required = true
            }
        }
    }
}
