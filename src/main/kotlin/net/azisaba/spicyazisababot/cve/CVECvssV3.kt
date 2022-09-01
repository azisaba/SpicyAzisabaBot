package net.azisaba.spicyazisababot.cve

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getDouble
import net.azisaba.spicyazisababot.util.getString

data class CVECvssV3(
    val baseScore: Double,
    val baseSeverity: String,
    val attackComplexity: String,
    val attackVector: String,
    val availabilityImpact: String,
    val confidentialityImpact: String,
    val integrityImpact: String,
    val privilegesRequired: String,
    val scope: String,
    val userInteraction: String,
    val vectorString: String,
    val version: String,
) {
    companion object {
        fun read(obj: JsonObject): CVECvssV3 {
            val baseScore = obj.getDouble("baseScore")!!
            val baseSeverity = obj.getString("baseSeverity")!!
            val attackComplexity = obj.getString("attackComplexity")!!
            val attackVector = obj.getString("attackVector")!!
            val availabilityImpact = obj.getString("availabilityImpact")!!
            val confidentialityImpact = obj.getString("confidentialityImpact")!!
            val integrityImpact = obj.getString("integrityImpact")!!
            val privilegesRequired = obj.getString("privilegesRequired")!!
            val scope = obj.getString("scope")!!
            val userInteraction = obj.getString("userInteraction")!!
            val vectorString = obj.getString("vectorString")!!
            val version = obj.getString("version")!!
            return CVECvssV3(
                baseScore,
                baseSeverity,
                attackComplexity,
                attackVector,
                availabilityImpact,
                confidentialityImpact,
                integrityImpact,
                privilegesRequired,
                scope,
                userInteraction,
                vectorString,
                version,
            )
        }
    }
}
