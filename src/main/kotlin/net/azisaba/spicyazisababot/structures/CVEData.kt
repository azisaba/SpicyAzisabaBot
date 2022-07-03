package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObject
import net.azisaba.spicyazisababot.util.getString

data class CVEData(
    val dataFormat: String,
    val dataType: String,
    val dataVersion: String,
    val meta: CVEDataMeta,
    val description: CVEDataDescription,
    val problemType: CVEDataProblemType,
    val references: CVEDataReference,
) {
    companion object {
        fun read(obj: JsonObject): CVEData {
            val dataFormat = obj.getString("data_format")!!
            val dataType = obj.getString("data_type")!!
            val dataVersion = obj.getString("data_version")!!
            val meta = CVEDataMeta.read(obj.getObject("CVE_data_meta")!!)
            val description = CVEDataDescription.read(obj.getObject("description")!!)
            val problemType = CVEDataProblemType.read(obj.getObject("problemtype")!!)
            val references = CVEDataReference.read(obj.getObject("references")!!)
            return CVEData(dataFormat, dataType, dataVersion, meta, description, problemType, references)
        }
    }
}
