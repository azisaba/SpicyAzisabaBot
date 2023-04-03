package net.azisaba.spicyazisababot.commands

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.azisaba.spicyazisababot.cve.CVEResult
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.getObject
import java.io.FileNotFoundException

object CveCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val idString = interaction.optString("id") ?: return
        val groups = "^(?i)(?:CVE-)?(\\d+)-(\\d+)(/s)?$".toRegex().matchEntire(idString)?.groupValues ?: return
        val year = groups[1].toInt()
        val number = groups[2].toInt()
        val short = groups[3].isNotEmpty()
        val defer = interaction.deferPublicResponse()
        val text = try {
            Util.executeCachedTextRequest("https://services.nvd.nist.gov/rest/json/cve/1.0/CVE-$year-$number", 1000 * 60 * 60 * 2) // 2 hours
        } catch (e: FileNotFoundException) {
            defer.respond { content = "Unable to find vuln CVE-$year-$number" }
            return
        }
        val obj = Json.parseToJsonElement(text).jsonObject
        val cve = CVEResult.read(obj.getObject("result")!!.jsonObject)
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
        defer.respond {
            this.embeds = embeds
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("cve", "Fetch CVE entry") {
            string("id", "CVE-yyyy-xxxxx") {
                required = true
                minLength = 10
            }
        }
    }
}