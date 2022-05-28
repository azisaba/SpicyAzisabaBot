package net.azisaba.spicyAzisabaBot.messages

import dev.kord.common.Color
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.azisaba.spicyAzisabaBot.util.Util
import org.json.JSONObject
import util.http.RESTAPI
import util.http.RESTAPI.BodyBuilder

// apparently these applications are available (or known)
//  youtube: '880218394199220334'
//  // youtubedev: '880218832743055411' (probably discord dev only)
//  poker: '755827207812677713' (18+ only)
//  betrayal: '773336526917861400'
//  fishing: '814288819477020702'
//  chess: '832012774040141894'
//  chessdev: '832012586023256104'
//  lettertile: '879863686565621790'
//  wordsnack: '879863976006127627'
//  // doodlecrew: '878067389634314250'
//  awkword: '879863881349087252'
//  spellcast: '852509694341283871'
//  checkers: '832013003968348200'
//  // puttparty: '763133495793942528'
//  sketchheads: '902271654783242291'
//  blazing8s: '832025144389533716'
//  // sketchyartist: '879864070101172255'

object YouTubeMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.getAuthorAsMember()?.isBot == false &&
            (message.content == "/youtube" ||
            message.content.startsWith("/youtube "))

    override suspend fun handle(message: Message) {
        val channelId = message.getAuthorAsMember()!!.getVoiceState().channelId
        if (channelId == null) {
            message.channel.createEmbed {
                color = Color(0xFF0000)
                title = "Error"
                description = "You are not in a voice channel."
            }
            return
        }
        val args = message.content.split("\\s+".toRegex()).drop(1)
        val applicationId = if (args.isEmpty()) {
            "880218394199220334" // youtube
        } else {
            args[0]
        }
        val json = JSONObject()
            .put("max_age", 86400)
            .put("max_uses", 0)
            .put("target_application_id", applicationId)
            .put("target_type", 2)
            .put("temporary", false)
            .put("validate", JSONObject.NULL)
        val api = RESTAPI(
            "https://discord.com/api/v8/channels/${channelId}/invites",
            "POST",
            BodyBuilder()
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
            message.channel.createEmbed {
                color = Color(0xFF0000)
                title = "Error"
                description = "Discord API returned: ${response.rawResponse}"
            }
            return
        }
        message.channel.createMessage {
            content = "https://discord.gg/$code"
        }
    }
}
