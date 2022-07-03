package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObjectArray

data class CVEDataProblemType(
    val data: List<CVEDataProblemTypeData>,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataProblemType {
            val data = obj.getObjectArray("problemtype_data")!!.map { CVEDataProblemTypeData.read(it) }
            return CVEDataProblemType(data)
        }
    }
}
