package net.azisaba.spicyazisababot.commands

import dev.kord.common.Color
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.kordLogger
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optSnowflake
import net.azisaba.spicyazisababot.util.Util.optString
import org.json.JSONObject
import util.http.RESTAPI

object YouTubeCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction) = interaction.channel.getGuildOrNull() != null

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val applicationId = interaction.optString("type") ?: "880218394199220334" // defaults to youtube
        val channelId = interaction.optSnowflake("channel")
            ?: interaction.getChannel().getGuildOrNull()!!.getMember(interaction.user.id).getVoiceStateOrNull()?.channelId
            ?: return sendNotInVCError(interaction)
        val code = try {
            createInvite(channelId, applicationId)
        } catch (e: Exception) {
            kordLogger.error(e) { "Failed to create invite for channel $channelId" }
            interaction.respondEphemeral {
                embed {
                    color = Color(0xFF0000)
                    title = "Error"
                    description = e.message
                }
            }
            return
        }
        interaction.respondPublic {
            content = "https://discord.gg/$code"
        }
    }

    suspend fun createInvite(channelId: Snowflake, applicationId: String): String {
        val json = JSONObject()
            .put("max_age", 86400)
            .put("max_uses", 0)
            .put("target_application_id", applicationId)
            .put("target_type", 2)
            .put("temporary", false)
            .put("validate", JSONObject.NULL)
        val api = RESTAPI(
            "https://discord.com/api/v10/channels/$channelId/invites",
            "POST",
            RESTAPI.BodyBuilder()
                .addRequestProperty("Content-Type", "application/json")
                .addRequestProperty("Authorization", "Bot ${Util.getEnvOrThrow("BOT_TOKEN")}")
                .addRequestProperty("User-Agent", "SpicyAzisabaBot https://github.com/azisaba/SpicyAzisabaBot")
                .setJSON(json)
                .build())
        val response = withContext(Dispatchers.IO) {
            api.call().complete()
        }
        val code = response.response?.get("code")
        if (code == null || code !is String) {
            throw RuntimeException("Discord API returned: ${response.rawResponse}")
        }
        return code
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("youtube", "Starts an activity in the voice channel.") {
            string("type", "Activity to play") {
                required = false
                choice("Sketch Heads", "902271654783242291")
                choice("YouTube", "880218394199220334")
                choice("Poker Night (18+)", "755827207812677713")
                choice("Checkers in the Park", "832013003968348200")
                choice("Chess in the Park", "832012774040141894")
                choice("Fishing", "814288819477020702")
                choice("Letter Tile", "879863686565621790")
                choice("Word Snack", "879863976006127627")
                choice("Awkword", "879863881349087252")
                choice("SpellCast", "852509694341283871")
                choice("Blazing 8s", "832025144389533716")
            }
            channel("channel", "Voice channel to create the invite in.") {
                required = false
                channelTypes = listOf(ChannelType.GuildVoice)
            }
            dmPermission = false
        }
    }

    private suspend fun sendNotInVCError(interaction: ApplicationCommandInteraction) {
        interaction.respondEphemeral {
            embed {
                color = Color(0xFF0000)
                title = "Error"
                description = "You are not in a voice channel. (You can also specify the channel with option `channel`)"
            }
        }
    }
}
