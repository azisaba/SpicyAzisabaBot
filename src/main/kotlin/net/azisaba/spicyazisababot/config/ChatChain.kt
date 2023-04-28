package net.azisaba.spicyazisababot.config

import kotlinx.serialization.Serializable

@Serializable
data class ChatChain(
    val prompt: String,
    val chain: Map<String, ChatChain> = emptyMap(),
)
