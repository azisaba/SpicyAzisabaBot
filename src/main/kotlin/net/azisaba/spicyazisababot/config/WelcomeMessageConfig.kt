package net.azisaba.spicyazisababot.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
data class WelcomeMessageConfig(
    val excludeBot: Boolean = true,
    @YamlComment("参加時にメッセージが送信されるチャンネルを指定します。(チャンネルからサーバーも同時に指定されます)")
    val channelId: String = "",
    @YamlComment(
        "メッセージを指定します。リストは改行(\\n)で連結され、チャンネルに送信されます。",
        "- {mention} : 参加したユーザーに対するメンション",
        "- {tag} : ユーザーのタグ(ユーザー#0000)"
    )
    val messageLines: List<String> = listOf(
        "{mention}",
        "__新人のやることリスト__",
        "・規約を確認して同意する https://example.com/terms",
        "・<#000000000000>でPIN留めされたテンプレート通りに自己紹介を行う。",
        "・ディスコードのニックネームにMCIDを明記する（形式はほかのユーザーを参考に）",
    )
)
