package net.azisaba.spicyAzisabaBot.util

import dev.kord.common.entity.Snowflake

object Constant {
    val REAL_PROBLEM_CHANNEL_ID = Snowflake(System.getenv("REAL_PROBLEM_CHANNEL_ID")?.toLongOrNull() ?: 760438206847123497L)
    val DEVELOPER_ROLE = Snowflake(System.getenv("DEVELOPER_ROLE_ID")?.toLongOrNull() ?: 695920999803125781L)
    val SELF_INTRO_CHANNEL = System.getenv("SELF_INTRO_CHANNEL") ?: "720660766952390696"
    val LEAVE_LOG_CHANNEL = Snowflake(System.getenv("LEAVE_LOG_CHANNEL")?.toLongOrNull() ?: 984491166466719774L)
}
