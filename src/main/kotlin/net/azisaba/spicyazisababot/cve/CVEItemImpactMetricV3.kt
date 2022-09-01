package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getDouble
import net.azisaba.spicyazisababot.util.getObject

data class CVEItemImpactMetricV3(
    val exploitabilityScore: Double,
    val impactScore: Double,
    val cvssV3: CVECvssV3,
) {
    companion object {
        fun read(obj: JsonObject): CVEItemImpactMetricV3 {
            val exploitabilityScore = obj.getDouble("exploitabilityScore")!!
            val impactScore = obj.getDouble("impactScore")!!
            val cvssV3 = CVECvssV3.read(obj.getObject("cvssV3")!!)
            return CVEItemImpactMetricV3(exploitabilityScore, impactScore, cvssV3)
        }
    }
}
