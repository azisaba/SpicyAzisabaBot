package net.azisaba.spicyazisababot.messages

import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.structures.CVEResult
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.getObject
import java.io.FileNotFoundException

object CVEMessageHandler: MessageHandler {
    override suspend fun canProcess(message: Message): Boolean = message.content.matches("^(?i)/CVE-\\d+-\\d+(/s)?$".toRegex())

    override suspend fun handle(message: Message) {
        if (message.author?.isBot != false) return
        val groups = "^(?i)/CVE-(\\d+)-(\\d+)(/s)?$".toRegex().matchEntire(message.content)?.groupValues ?: return
        val year = groups[1].toInt()
        val number = groups[2].toInt()
        val short = groups[3].isNotEmpty()
        val text = try {
            Util.executeCachedTextRequest("https://services.nvd.nist.gov/rest/json/cve/1.0/CVE-$year-$number", 1000 * 60 * 60 * 2) // 2 hours
        } catch (e: FileNotFoundException) {
            message.channel.createMessage("Unable to find vuln CVE-$year-$number")
            return
        }
        val obj = Json.decodeFromString<JsonObject>(text)
        val result = obj.getObject("result")!!
        val cve = CVEResult.read(result)
        val embeds = mutableListOf<EmbedBuilder>()
        cve.items.forEach { item ->
            val builder = EmbedBuilder()
            builder.title = item.cve.meta.id
            builder.url = "https://nvd.nist.gov/vuln/detail/${item.cve.meta.id}"
            builder.description = "*Source: ${item.cve.meta.assigner}*\n"
            builder.description += if (item.cve.description.data.isNotEmpty()) item.cve.description.data[0].value else "*No description provided*"
            if (item.impact.baseMetricV3 != null) {
                builder.field("Severity (CVSS 3.x)") {
                    "**Base Score**: ${item.impact.baseMetricV3.cvssV3.baseScore} ${item.impact.baseMetricV3.cvssV3.baseSeverity}\n**Vector**: ${item.impact.baseMetricV3.cvssV3.vectorString}"
                }
            }
            if (item.impact.baseMetricV2 != null) {
                builder.field("Severity (CVSS 2.0)") {
                    "**Base Score**: ${item.impact.baseMetricV2.cvssV2.baseScore} ${item.impact.baseMetricV2.severity}\n**Vector**: ${item.impact.baseMetricV2.cvssV2.vectorString}"
                }
            }
            if (!short) {
                val lines = item.cve.references.referenceData.map { "[${it.name.replace("[", "\\[")}](${it.url})" }
                val mergedReferences = mutableListOf<String>()
                var currentString = ""
                lines.forEach { s ->
                    if (currentString.length + s.length > 1024) {
                        mergedReferences.add(currentString)
                        currentString = "$s\n"
                    } else {
                        currentString += "$s\n"
                    }
                }
                if (currentString.isNotEmpty()) {
                    mergedReferences.add(currentString)
                }
                mergedReferences.forEachIndexed { index, s ->
                    builder.field("References (Page ${index+1})") { s }
                }
            }
            embeds.add(builder)
        }
        message.channel.createMessage {
            this.embeds.addAll(embeds)
        }
    }
}
