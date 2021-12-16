package net.azisaba.spicyAzisabaBot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyAzisabaBot.util.getObjectArray

data class CVEDataDescription(
    val data: List<CVELangValueEntry>,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataDescription {
            val data = obj.getObjectArray("description_data")!!.map { CVELangValueEntry.read(it) }
            return CVEDataDescription(data)
        }
    }
}
