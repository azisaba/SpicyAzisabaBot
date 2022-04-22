package net.azisaba.spicyAzisabaBot.util

import dev.kord.common.entity.Snowflake

object Constant {
    val REAL_PROBLEM_CHANNEL_ID = Snowflake(System.getenv("REAL_PROBLEM_CHANNEL_ID")?.toLongOrNull() ?: 760438206847123497L)
    val DEVELOPER_ROLE = Snowflake(System.getenv("DEVELOPER_ROLE_ID")?.toLongOrNull() ?: 695920999803125781L)
}
