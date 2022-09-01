package net.azisaba.spicyazisababot.permission

import dev.kord.common.entity.Snowflake

data class PermissionData(
    val id: Snowflake,
    val type: PermissionType,
    val guildId: Snowflake,
    val node: String,
    val value: Boolean,
)
