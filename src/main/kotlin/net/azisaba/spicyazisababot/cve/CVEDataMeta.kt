package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getString

data class CVEDataMeta(
    val assigner: String,
    val id: String,
) {
    companion object {
        fun read(obj: JsonObject): CVEDataMeta {
            val assigner = obj.getString("ASSIGNER")!!
            val id = obj.getString("ID")!!
            return CVEDataMeta(assigner, id)
        }
    }
}
