package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getObject

data class CVEItemImpact(
    val baseMetricV3: CVEItemImpactMetricV3?,
    val baseMetricV2: CVEItemImpactMetricV2?,
) {
    companion object {
        fun read(obj: JsonObject): CVEItemImpact {
            val baseMetricV3 = obj.getObject("baseMetricV3")?.let { CVEItemImpactMetricV3.read(it) }
            val baseMetricV2 = obj.getObject("baseMetricV2")?.let { CVEItemImpactMetricV2.read(it) }
            return CVEItemImpact(baseMetricV3, baseMetricV2)
        }
    }
}
