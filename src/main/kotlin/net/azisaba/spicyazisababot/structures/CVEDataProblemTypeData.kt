package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObjectArray

data class CVEDataProblemTypeData(
    val description: List<CVELangValueEntry>,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataProblemTypeData {
            val description = obj.getObjectArray("description")!!.map { CVELangValueEntry.read(it) }
            return CVEDataProblemTypeData(description)
        }
    }
}
