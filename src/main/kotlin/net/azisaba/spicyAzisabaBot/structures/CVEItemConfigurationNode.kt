package net.azisaba.spicyAzisabaBot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyAzisabaBot.util.getObjectArray
import net.azisaba.spicyAzisabaBot.util.getString

data class CVEItemConfigurationNode(
    val operator: String,
    val cpeMatch: List<CVEItemConfigurationNodeCPEMatch>,
    val children: List<Any> = listOf(),
) {
    companion object {
        fun read(obj: JsonObject): CVEItemConfigurationNode {
            val operator = obj.getString("operator")!!
            val cpeMatch = obj.getObjectArray("cpe_match")!!.map { CVEItemConfigurationNodeCPEMatch.read(it) }
            return CVEItemConfigurationNode(operator, cpeMatch)
        }
    }
}
