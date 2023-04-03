package net.azisaba.spicyazisababot.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
data class LeaveMessageConfig(
    val excludeBot: Boolean = false,
    @YamlComment("対象とするサーバーIDを指定します(Stringのリスト)。nullの場合はchannelIdから取得されるギルドが自動的に使用されます。")
    val guildIds: List<String>? = null,
    @YamlComment("退出時にメッセージが送信されるチャンネルを指定します。(チャンネルからサーバーも同時に指定されます)")
    val channelId: String = "",
    @YamlComment(
        "メッセージを指定します。リストは改行(\\n)で連結され、チャンネルに送信されます。",
        "- {user.tag} : 退出したユーザーのタグ(名前#0000)",
        "- {user.id} : ユーザーID",
        "- {user.isBot} : ユーザーがBotかどうか",
        "- {github} : 退出したユーザーのGitHub(連携されていない場合はnull)",
        "(以下はデータを取得できなかった場合はすべてnullになります)",
        "- {old.nickname} : ニックネーム",
        "- {old.roleNames} : 付与されていたロールの名前",
        "- {old.roleIds} : 付与されていたロールのID",
        "- {old.joinedAt.epochSeconds} : 参加した日時のUnix Epoch秒"
    )
    val messageLines: List<String> = listOf(
        ":outbox_tray: `{user.tag}` <@{user.id}> (ID: {user.id}, Bot: {user.isBot})",
        "GitHub: {github}",
        "参加日時: <t:{old.joinedAt.epochSeconds}:F> <t:{old.joinedAt.epochSeconds}:R>",
        "ニックネーム:",
        "```",
        "{old.nickname}",
        "```",
        "ロール:",
        "```",
        "{old.roleNames}",
        "",
        "(ID: {old.roleIds})",
        "```",
    )
)
