package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getBoolean
import net.azisaba.spicyazisababot.util.getDouble
import net.azisaba.spicyazisababot.util.getObject
import net.azisaba.spicyazisababot.util.getString

data class CVEItemImpactMetricV2(
    val exploitabilityScore: Double,
    val impactScore: Double,
    val acInsufInfo: Boolean,
    val obtainAllPrivilege: Boolean,
    val obtainOtherPrivilege: Boolean,
    val obtainUserPrivilege: Boolean,
    val userInteractionRequired: Boolean,
    val severity: String,
    val cvssV2: CVECvssV2,
) {
    companion object {
        fun read(obj: JsonObject): CVEItemImpactMetricV2 {
            val exploitabilityScore = obj.getDouble("exploitabilityScore")!!
            val impactScore = obj.getDouble("impactScore")!!
            val acInsufInfo = obj.getBoolean("acInsufInfo")!!
            val obtainAllPrivilege = obj.getBoolean("obtainAllPrivilege")!!
            val obtainOtherPrivilege = obj.getBoolean("obtainOtherPrivilege")!!
            val obtainUserPrivilege = obj.getBoolean("obtainUserPrivilege")!!
            val userInteractionRequired = obj.getBoolean("userInteractionRequired")!!
            val severity = obj.getString("severity")!!
            val cvssV2 = CVECvssV2.read(obj.getObject("cvssV2")!!)
            return CVEItemImpactMetricV2(
                exploitabilityScore,
                impactScore,
                acInsufInfo,
                obtainAllPrivilege,
                obtainOtherPrivilege,
                obtainUserPrivilege,
                userInteractionRequired,
                severity,
                cvssV2,
            )
        }
    }
}
