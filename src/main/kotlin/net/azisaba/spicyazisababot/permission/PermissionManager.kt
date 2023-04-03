package net.azisaba.spicyazisababot.permission

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import kotlinx.coroutines.flow.toList
import net.azisaba.spicyazisababot.util.Util
import java.math.BigInteger
import java.sql.Types

object PermissionManager {
    private const val MAX_PERMISSION_NODES = 10

    suspend fun check(member: Member, node: String): PermissionCheckResult {
        val userValue = userCheck(member.guildId, member.id, node)
        if (userValue != null) {
            return PermissionCheckResult(userValue, "User ${member.id} has `$node` set to $userValue")
        }
        val map = mutableMapOf<Snowflake, Boolean?>()
        Util.getConnection().use { connection ->
            val ids = member.roleIds.map { "\"$it\"" }.toMutableList()
            ids.add(0, "\"0\"")
            connection.prepareStatement("SELECT `id`, `value` FROM `role_permissions` WHERE `guild_id` = ? AND (`id` IN (${ids.joinToString()})) AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(member.guildId.toString()), Types.BIGINT)
                statement.setString(2, node)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        map[Snowflake(result.getBigDecimal("id").toBigInteger().toString(10))] = result.getBoolean("value")
                    }
                }
            }
        }
        member.roles.toList().sortedByDescending { it.rawPosition }.forEach { role ->
            val roleValue = map[role.id]
            if (roleValue != null) {
                return PermissionCheckResult(roleValue, "Role ${role.id} has `$node` set to $roleValue")
            }
        }
        val everyoneValue = map[Snowflake(0)]
        if (everyoneValue != null) {
            return PermissionCheckResult(everyoneValue, "Role 0 (everyone) has `$node` set to $everyoneValue")
        }
        return PermissionCheckResult(null, "No roles have `$node` set and the user does not have permission set.")
    }

    fun globalCheck(userId: Snowflake, node: GlobalPermissionNode): PermissionCheckResult {
        val userValue = globalUserCheck(userId, node)
        if (userValue != null) {
            return PermissionCheckResult(userValue, "User $userId has `$node` set to $userValue")
        }
        val defaultValue = globalUserCheck(Snowflake(0), node)
        if (defaultValue != null) {
            return PermissionCheckResult(defaultValue, "`$node` set to $defaultValue by default")
        }
        return PermissionCheckResult(null, "User does not have permission `$node` set and there is no default permission set.")
    }

    fun userCheck(guildId: Snowflake, userId: Snowflake, node: String): Boolean? =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `value` FROM `user_permissions` WHERE `guild_id` = ? AND `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(3, node)
                val rs = statement.executeQuery()
                if (rs.next()) {
                    rs.getBoolean("value")
                } else {
                    null
                }
            }
        }

    fun userSet(guildId: Snowflake, userId: Snowflake, node: String, value: Boolean = true) {
        val size = userList(guildId, userId).size
        if (size >= MAX_PERMISSION_NODES) {
            throw IllegalArgumentException("User has too many permission nodes (current $size >= max $MAX_PERMISSION_NODES)")
        }
        Util.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO `user_permissions` (`guild_id`, `id`, `node`, `value`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(3, node)
                statement.setBoolean(4, value)
                statement.executeUpdate()
            }
        }
    }

    fun userUnset(guildId: Snowflake, userId: Snowflake, node: String) {
        Util.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM `user_permissions` WHERE `guild_id` = ? AND `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(3, node)
                statement.executeUpdate()
            }
        }
    }

    fun userList(guildId: Snowflake, userId: Snowflake): List<PermissionData> =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `node`, `value` FROM `user_permissions` WHERE `guild_id` = ? AND `id` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(userId.toString()), Types.BIGINT)
                val rs = statement.executeQuery()
                val list = mutableListOf<PermissionData>()
                while (rs.next()) {
                    list.add(PermissionData(userId, PermissionType.USER, guildId, rs.getString("node"), rs.getBoolean("value")))
                }
                list
            }
        }

    fun globalUserCheck(userId: Snowflake, node: GlobalPermissionNode): Boolean? =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `value` FROM `global_permissions` WHERE `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(2, node.node)
                val rs = statement.executeQuery()
                if (rs.next()) {
                    rs.getBoolean("value")
                } else {
                    null
                }
            }
        }

    fun globalUserSet(userId: Snowflake, node: GlobalPermissionNode, value: Boolean = true) {
        Util.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO `global_permissions` (`id`, `node`, `value`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)").use { statement ->
                statement.setObject(1, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(2, node.node)
                statement.setBoolean(3, value)
                statement.executeUpdate()
            }
        }
    }

    fun globalUserUnset(userId: Snowflake, node: GlobalPermissionNode) {
        Util.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM `global_permissions` WHERE `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(userId.toString()), Types.BIGINT)
                statement.setString(2, node.node)
                statement.executeUpdate()
            }
        }
    }

    fun globalUserList(userId: Snowflake): List<GlobalPermissionData> =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `node`, `value` FROM `global_permissions` WHERE `id` = ?").use { statement ->
                statement.setObject(1, BigInteger(userId.toString()), Types.BIGINT)
                val rs = statement.executeQuery()
                val list = mutableListOf<GlobalPermissionData>()
                while (rs.next()) {
                    val node = GlobalPermissionNode.nodeMap[rs.getString("node")] ?: continue
                    list.add(GlobalPermissionData(userId, node, rs.getBoolean("value")))
                }
                list
            }
        }

    fun roleCheck(guildId: Snowflake, roleId: Snowflake, node: String): Boolean? =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `value` FROM `role_permissions` WHERE `guild_id` = ? AND `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(roleId.toString()), Types.BIGINT)
                statement.setString(3, node)
                val rs = statement.executeQuery()
                if (rs.next()) {
                    rs.getBoolean("value")
                } else {
                    null
                }
            }
        }

    fun roleSet(guildId: Snowflake, roleId: Snowflake, node: String, value: Boolean = true) {
        val size = userList(guildId, roleId).size
        if (size >= MAX_PERMISSION_NODES) {
            throw IllegalArgumentException("Role has too many permission nodes (current $size >= max $MAX_PERMISSION_NODES)")
        }
        Util.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO `role_permissions` (`id`, `guild_id`, `node`, `value`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)").use { statement ->
                statement.setObject(1, BigInteger(roleId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setString(3, node)
                statement.setBoolean(4, value)
                statement.executeUpdate()
            }
        }
    }

    fun roleUnset(guildId: Snowflake, roleId: Snowflake, node: String) {
        Util.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM `role_permissions` WHERE `guild_id` = ? AND `id` = ? AND `node` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(roleId.toString()), Types.BIGINT)
                statement.setString(3, node)
                statement.executeUpdate()
            }
        }
    }

    fun roleList(guildId: Snowflake, roleId: Snowflake): List<PermissionData> =
        Util.getConnection().use { connection ->
            connection.prepareStatement("SELECT `node`, `value` FROM `role_permissions` WHERE `guild_id` = ? AND `id` = ?").use { statement ->
                statement.setObject(1, BigInteger(guildId.toString()), Types.BIGINT)
                statement.setObject(2, BigInteger(roleId.toString()), Types.BIGINT)
                val rs = statement.executeQuery()
                val list = mutableListOf<PermissionData>()
                while (rs.next()) {
                    list.add(PermissionData(roleId, PermissionType.ROLE, guildId, rs.getString("node"), rs.getBoolean("value")))
                }
                list
            }
        }
}
