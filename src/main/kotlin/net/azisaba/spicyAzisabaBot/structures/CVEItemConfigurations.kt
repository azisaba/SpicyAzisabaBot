package net.azisaba.spicyAzisabaBot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyAzisabaBot.util.getObjectArray
import net.azisaba.spicyAzisabaBot.util.getString

data class CVEItemConfigurations(
    val dataVersion: String,
    val nodes: List<CVEItemConfigurationNode>,
) {
    companion object {
        fun read(obj: JsonObject): CVEItemConfigurations {
            val dataVersion = obj.getString("CVE_data_version")!!
            val nodes = obj.getObjectArray("nodes")!!.map { CVEItemConfigurationNode.read(it) }
            return CVEItemConfigurations(dataVersion, nodes)
        }
    }
}
