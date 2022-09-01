package net.azisaba.spicyazisababot.commands

import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.BooleanBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.GroupCommandBuilder
import dev.kord.rest.builder.interaction.IntegerOptionBuilder
import dev.kord.rest.builder.interaction.RoleBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kord.rest.builder.interaction.UserBuilder
import dev.kord.rest.builder.interaction.group
import net.azisaba.spicyazisababot.permission.PermissionManager
import net.azisaba.spicyazisababot.permission.PermissionType
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optBoolean
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optSnowflake
import net.azisaba.spicyazisababot.util.Util.optString
import net.azisaba.spicyazisababot.util.Util.optSubCommands
import kotlin.math.max
import kotlin.math.min

object PermissionsCommand : CommandHandler {
    private var lastTableCreated = 0L

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        if (lastTableCreated + 1000 * 60 * 60 < System.currentTimeMillis()) {
            lastTableCreated = System.currentTimeMillis()
            Util.getConnection().use {
                it.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                            CREATE TABLE IF NOT EXISTS `user_permissions` (
                                `id` BIGINT UNSIGNED NOT NULL,
                                `guild_id` BIGINT UNSIGNED NOT NULL,
                                `node` VARCHAR(255) NOT NULL,
                                `value` TINYINT(1) NOT NULL DEFAULT 1,
                                PRIMARY KEY (`id`, `guild_id`, `node`)
                            )
                        """.trimIndent()
                    )
                    statement.executeUpdate(
                        """
                            CREATE TABLE IF NOT EXISTS `role_permissions` (
                                `id` BIGINT UNSIGNED NOT NULL,
                                `guild_id` BIGINT UNSIGNED NOT NULL,
                                `node` VARCHAR(255) NOT NULL,
                                `value` TINYINT(1) NOT NULL DEFAULT 1,
                                PRIMARY KEY (`id`, `guild_id`, `node`)
                            )
                        """.trimIndent()
                    )
                }
            }
        }
        interaction.optSubCommands("member", "set")?.apply {
            memberSet(interaction, options.optSnowflake("user")!!, options.optString("node")!!, options.optBoolean("value") ?: true)
        }
        interaction.optSubCommands("member", "unset")?.apply {
            memberUnset(interaction, options.optSnowflake("user")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("member", "check")?.apply {
            memberCheck(interaction, options.optSnowflake("user")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("member", "list")?.apply {
            memberOrRoleList(interaction, PermissionType.USER, options.optSnowflake("user")!!, options.optLong("page")?.toInt() ?: 1)
        }
        interaction.optSubCommands("role", "set")?.apply {
            roleSet(interaction, options.optSnowflake("role")!!, options.optString("node")!!, options.optBoolean("value") ?: true)
        }
        interaction.optSubCommands("role", "unset")?.apply {
            roleUnset(interaction, options.optSnowflake("role")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("role", "check")?.apply {
            roleCheck(interaction, options.optSnowflake("role")!!, options.optString("node")!!)
        }
        interaction.optSubCommands("role", "list")?.apply {
            memberOrRoleList(interaction, PermissionType.ROLE, options.optSnowflake("role")!!, options.optLong("page")?.toInt() ?: 1)
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("permissions", "Manage permissions.") {
            dmPermission = false
            defaultMemberPermissions = Permissions()
            group("member", "Manage permissions of a member.") {
                subCommand("set", "Set the permission.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to set the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to set.").apply {
                            this.required = true
                            this.maxLength = 255
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
                        },
                    )
                }
                subCommand("check", "Check the permission.") {
                    this.options = mutableListOf(
                        UserBuilder("user", "The user to check the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to check.").apply {
                            this.required = true
                            this.maxLength = 255
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
            }
            group("role", "Manage permission of a role.") {
                subCommand("set", "Set the permission.") {
                    this.options = mutableListOf(
                        RoleBuilder("role", "The role to set the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to set.").apply {
                            this.required = true
                            this.maxLength = 255
                        },
                        BooleanBuilder("value", "The permission value to set.").apply {
                            this.required = false
                        },
                    )
                }
                subCommand("unset", "Unset the permission.") {
                    this.options = mutableListOf(
                        RoleBuilder("role", "The role to unset the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to unset.").apply {
                            this.required = true
                            this.maxLength = 255
                        },
                    )
                }
                subCommand("check", "Check the permission.") {
                    this.options = mutableListOf(
                        UserBuilder("role", "The role to check the permission.").apply { this.required = true },
                        StringChoiceBuilder("node", "The permission node to check.").apply {
                            this.required = true
                            this.maxLength = 255
                        },
                    )
                }
                subCommand("list", "List the permissions of a role.") {
                    this.options = mutableListOf(
                        UserBuilder("role", "The role to list the permissions.").apply { this.required = true },
                        IntegerOptionBuilder("page", "The page to list.").apply {
                            this.required = false
                            this.minValue = 1
                            this.maxValue = Int.MAX_VALUE.toLong()
                        },
                    )
                }
            }
        }
    }

    private suspend fun memberSet(interaction: ApplicationCommandInteraction, user: Snowflake, node: String, value: Boolean) {
        PermissionManager.userSet(interaction.channel.getGuildOrNull()!!.id, user, node, value)
        interaction.respondPublic {
            content = "Set the permission node `$node` of user `$user` to `$value`."
        }
    }

    private suspend fun memberUnset(interaction: ApplicationCommandInteraction, user: Snowflake, node: String) {
        PermissionManager.userUnset(interaction.channel.getGuildOrNull()!!.id, user, node)
        interaction.respondPublic {
            content = "Unset the permission node `$node` of user `$user`."
        }
    }

    private suspend fun memberCheck(interaction: ApplicationCommandInteraction, userId: Snowflake, node: String) {
        val value = PermissionManager.check(interaction.channel.getGuildOrNull()!!.getMember(userId), node)
        interaction.respondPublic {
            content = "Permission check for `$node`:\n- Result: ${value.value}\n- Reason: ${value.reason}"
        }
    }

    private suspend fun roleSet(interaction: ApplicationCommandInteraction, roleId: Snowflake, node: String, value: Boolean) {
        PermissionManager.roleSet(interaction.channel.getGuildOrNull()!!.id, roleId, node, value)
        interaction.respondPublic {
            content = "Set the permission node `$node` of role `$roleId` to `$value`."
        }
    }

    private suspend fun roleUnset(interaction: ApplicationCommandInteraction, roleId: Snowflake, node: String) {
        PermissionManager.roleUnset(interaction.channel.getGuildOrNull()!!.id, roleId, node)
        interaction.respondPublic {
            content = "Unset the permission node `$node` of role `$roleId`."
        }
    }

    private suspend fun roleCheck(interaction: ApplicationCommandInteraction, roleId: Snowflake, node: String) {
        val value = PermissionManager.roleCheck(interaction.channel.getGuildOrNull()!!.id, roleId, node)
        interaction.respondPublic {
            content = "Role $roleId has permission node `$node` set to `$value`."
        }
    }

    private suspend fun memberOrRoleList(interaction: ApplicationCommandInteraction, type: PermissionType, entity: Snowflake, origPage: Int) {
        val permissions = if (type == PermissionType.USER) {
            PermissionManager.userList(interaction.channel.getGuildOrNull()!!.id, entity)
        } else {
            PermissionManager.roleList(interaction.channel.getGuildOrNull()!!.id, entity)
        }
        if (permissions.isEmpty()) {
            interaction.respondPublic {
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
        interaction.respondPublic {
            this.content = "The permissions of `$entity` (Page $page/$pages):\n$content"
        }
    }
}
