package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObjectArray

data class CVEDataReference(
    val referenceData: List<CVEDataReferenceData>,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataReference {
            val referenceData = obj.getObjectArray("reference_data")!!.map { CVEDataReferenceData.read(it) }
            return CVEDataReference(referenceData)
        }
    }
}
