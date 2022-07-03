package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObjectArray
import net.azisaba.spicyazisababot.util.getString

data class CVEResult(
    val type: String,
    val dataFormat: String,
    val dataVersion: String,
    val dataTimestamp: String,
    val items: List<CVEItem>,
) {
    companion object {
        fun read(obj: JsonObject): CVEResult {
            val type = obj.getString("CVE_data_type")!!
            val dataFormat = obj.getString("CVE_data_format")!!
            val dataVersion = obj.getString("CVE_data_version")!!
            val dataTimestamp = obj.getString("CVE_data_timestamp")!!
            val items = obj.getObjectArray("CVE_Items")!!.map { CVEItem.read(it) }
            return CVEResult(type, dataFormat, dataVersion, dataTimestamp, items)
        }
    }
}
