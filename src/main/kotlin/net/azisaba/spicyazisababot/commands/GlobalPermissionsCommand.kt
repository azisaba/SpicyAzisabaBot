package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.BooleanBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.IntegerOptionBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kord.rest.builder.interaction.UserBuilder
import dev.kord.rest.builder.interaction.group
import net.azisaba.spicyazisababot.permission.GlobalPermissionNode
import net.azisaba.spicyazisababot.permission.PermissionManager
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optBoolean
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optSnowflake
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.Util.optSubCommands
import kotlin.math.max
import kotlin.math.min

object GlobalPermissionsCommand : CommandHandler {
    private var lastTableCreated = 0L

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        if (lastTableCreated + 1000 * 60 * 60 < System.currentTimeMillis()) {
            lastTableCreated = System.currentTimeMillis()
            Util.getConnection().use {
                it.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                            CREATE TABLE IF NOT EXISTS `global_permissions` (
                                `id` BIGINT UNSIGNED NOT NULL,
                                `node` VARCHAR(255) NOT NULL,
                                `value` TINYINT(1) NOT NULL DEFAULT 1,
                                PRIMARY KEY (`id`, `node`)
                            )
                        """.trimIndent()
                    )
                }
            }
        }
        val defer = interaction.deferPublicResponse()
        if (PermissionManager.globalUserCheck(interaction.user.id, GlobalPermissionNode.EditGlobalPermissions) != true) {
            defer.respond { content = "このコマンドを使用する権限がありません。" }
            return
        }
        interaction.optSubCommands("member", "set")?.apply {
            memberSet(defer, options.optSnowflake("user")!!, options.optString("node")!!, options.optBoolean("value") ?: true)
        }
        interaction.optSubCommands("member", "unset")?.apply {
            memberUnset(defer, options.optSnowflake("user")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("member", "list")?.apply {
            memberList(defer, options.optSnowflake("user")!!, options.optLong("page")?.toInt() ?: 1)
        }
        interaction.optSubCommands("member", "check")?.apply {
            memberCheck(defer, options.optSnowflake("user")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("default", "set")?.apply {
            memberSet(defer, Snowflake(0), options.optString("node")!!, options.optBoolean("value") ?: true)
        }
        interaction.optSubCommands("default", "unset")?.apply {
            memberUnset(defer, Snowflake(0), options.optString("node")!!)
        }
        interaction.optSubCommands("default", "list")?.apply {
            memberList(defer, Snowflake(0), options.optLong("page")?.toInt() ?: 1)
        }
    }

    @Suppress("DuplicatedCode")
    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("gpedit", "Edit global permission.") {
            defaultMemberPermissions = Permissions()
            group("member", "Manage permissions of a user.") {
                subCommand("set", "Set the permission.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to set the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to set.").apply {
                            this.required = true
                            this.maxLength = 255
                            GlobalPermissionNode.values().forEach { choice(it.name, it.node) }
                        },
                        BooleanBuilder("value", "The permission value to set.").apply {
                            this.required = false
                        },
                    )
                }
                subCommand("unset", "Unset the permission.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to unset the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to unset.").apply {
                            this.required = true
                            this.maxLength = 255
                            GlobalPermissionNode.values().forEach { choice(it.name, it.node) }
                        },
                    )
                }
                subCommand("list", "List the permissions of a member.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to list the permissions.").apply { this.required = true },
                        IntegerOptionBuilder("page", "The page to list.").apply {
                            this.required = false
                            this.minValue = 1
                            this.maxValue = Int.MAX_VALUE.toLong()
                        },
                    )
                }
                subCommand("check", "Check the permissions of a member.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to list the permissions.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to unset.").apply {
                            this.required = true
                            this.maxLength = 255
                            GlobalPermissionNode.values().forEach { choice(it.name, it.node) }
                        },
                    )
                }
            }
            group("default", "Manage default permissions") {
                subCommand("set", "Set the permission.") {
                    this.options = mutableListOf(
                        StringChoiceBuilder("node", "The permission node to set.").apply {
                            this.required = true
                            this.maxLength = 255
                            GlobalPermissionNode.values().forEach { choice(it.name, it.node) }
                        },
                        BooleanBuilder("value", "The permission value to set.").apply {
                            this.required = false
                        },
                    )
                }
                subCommand("unset", "Unset the permission.") {
                    this.options = mutableListOf(
                        StringChoiceBuilder("node", "The permission node to unset.").apply {
                            this.required = true
                            this.maxLength = 255
                            GlobalPermissionNode.values().forEach { choice(it.name, it.node) }
                        },
                    )
                }
                subCommand("list", "List the permissions.") {}
            }
        }
    }

    private suspend fun memberSet(defer: DeferredMessageInteractionResponseBehavior, user: Snowflake, node: String, value: Boolean) {
        PermissionManager.globalUserSet(user, GlobalPermissionNode.nodeMap[node]!!, value)
        defer.respond {
            content = "Set the permission node `$node` of user `$user` to `$value`."
        }
    }

    private suspend fun memberUnset(defer: DeferredMessageInteractionResponseBehavior, user: Snowflake, node: String) {
        PermissionManager.globalUserUnset(user, GlobalPermissionNode.nodeMap[node]!!)
        defer.respond {
            content = "Unset the permission node `$node` of user `$user`."
        }
    }

    private suspend fun memberCheck(defer: DeferredMessageInteractionResponseBehavior, userId: Snowflake, node: String) {
        val value = PermissionManager.globalCheck(userId, GlobalPermissionNode.nodeMap[node]!!)
        defer.respond {
            content = "Permission check for `$node`:\n- Result: ${value.value}\n- Reason: ${value.reason}"
        }
    }

    private suspend fun memberList(defer: DeferredMessageInteractionResponseBehavior, entity: Snowflake, origPage: Int) {
        val permissions = PermissionManager.globalUserList(entity)
        if (permissions.isEmpty()) {
            defer.respond {
                content = "`$entity` has no permissions set."
            }
            return
        }
        val pages = permissions.size / 10 + if (permissions.size % 10 == 0) 0 else 1
        val page = if (origPage < 1 || origPage > pages) {
            max(1, min(pages, origPage))
        } else {
            origPage
        }
        val start = (page - 1) * 10
        val end = start + 10
        val content = permissions.subList(start, end.coerceAtMost(permissions.size)).joinToString("\n") {
            "`${it.node}`: ${it.value}"
        }
        defer.respond {
            this.content = "Permissions of `$entity` (Page $page/$pages):\n$content"
        }
    }
}
