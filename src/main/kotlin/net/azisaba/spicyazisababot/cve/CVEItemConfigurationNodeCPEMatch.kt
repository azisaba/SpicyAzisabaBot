package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getBoolean
import net.azisaba.spicyazisababot.util.getString

data class CVEItemConfigurationNodeCPEMatch(
    val vulnerable: Boolean,
    val cpe23Uri: String,
    val versionStartIncluding: String? = null,
    val versionEndExcluding: String? = null,
    val cpeName: List<Any> = listOf(),
) {
    companion object {
        fun read(obj: JsonObject): CVEItemConfigurationNodeCPEMatch {
            val vulnerable: Boolean = obj.getBoolean("vulnerable")!!
            val cpe23Uri: String = obj.getString("cpe23Uri")!!
            val versionStartIncluding: String? = obj.getString("versionStartIncluding")
            val versionEndExcluding: String? = obj.getString("versionEndExcluding")
            return CVEItemConfigurationNodeCPEMatch(vulnerable, cpe23Uri, versionStartIncluding, versionEndExcluding)
        }
    }
}
