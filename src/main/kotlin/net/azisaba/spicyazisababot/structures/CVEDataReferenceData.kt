package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getString
import net.azisaba.spicyazisababot.util.getStringArray

data class CVEDataReferenceData(
    val url: String,
    val name: String,
    val refSource: String,
    val tags: List<String>,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataReferenceData {
            val url = obj.getString("url")!!
            val name = obj.getString("name")!!
            val refSource = obj.getString("refsource")!!
            val tags = obj.getStringArray("tags")!!.filterNotNull()
            return CVEDataReferenceData(url, name, refSource, tags)
        }
    }
}
