package net.azisaba.spicyazisababot.config.command

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
data class CustomCommandDefinition(
    val name: String,
    val description: String,
    @YamlComment("Public or Ephemeral")
    val responseType: ResponseType,
    val config: CustomCommandConfig,
) {
    enum class ResponseType {
        Public,
        Ephemeral,
    }
}
