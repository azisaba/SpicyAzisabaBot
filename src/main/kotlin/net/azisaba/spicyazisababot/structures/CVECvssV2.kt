package net.azisaba.spicyazisababot.structures

import kotlinx.serialization.json.JsonObject
import net.azisaba.spicyazisababot.util.getDouble
import net.azisaba.spicyazisababot.util.getString

data class CVECvssV2(
    val baseScore: Double,
    val accessComplexity: String,
    val accessVector: String,
    val availabilityImpact: String,
    val confidentialityImpact: String,
    val integrityImpact: String,
    val authentication: String,
    val vectorString: String,
    val version: String,
) {
    companion object {
        fun read(obj: JsonObject): CVECvssV2 {
            val baseScore = obj.getDouble("baseScore")!!
            val accessComplexity = obj.getString("accessComplexity")!!
            val accessVector = obj.getString("accessVector")!!
            val availabilityImpact = obj.getString("availabilityImpact")!!
            val confidentialityImpact = obj.getString("confidentialityImpact")!!
            val integrityImpact = obj.getString("integrityImpact")!!
            val authentication = obj.getString("authentication")!!
            val vectorString = obj.getString("vectorString")!!
            val version = obj.getString("version")!!
            return CVECvssV2(
                baseScore,
                accessComplexity,
                accessVector,
                availabilityImpact,
                confidentialityImpact,
                integrityImpact,
                authentication,
                vectorString,
                version,
            )
        }
    }
}
