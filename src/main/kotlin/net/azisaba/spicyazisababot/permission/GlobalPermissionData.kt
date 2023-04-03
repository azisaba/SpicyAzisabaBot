package net.azisaba.spicyazisababot.permission

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable

@Serializable
data class GlobalPermissionData(
    val id: Snowflake,
    val node: GlobalPermissionNode,
    val value: Boolean,
)
