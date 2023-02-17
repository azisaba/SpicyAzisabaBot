package net.azisaba.spicyazisababot

import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
)
