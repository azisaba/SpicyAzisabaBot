package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.flow.toList

object StatsCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction) = interaction.channel.getGuildOrNull() != null

    @OptIn(PrivilegedIntent::class)
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val guild = interaction.channel.getGuildOrNull() ?: return
        interaction.respondEphemeral {
            embed {
                this.title = "Stats"
                this.field("サーバー人数") {
                    var botCount = 0
                    var nonBotCount = 0
                    guild.requestMembers().collect {
                        it.members.forEach { member ->
                            if (member.isBot) {
                                botCount++
                            } else {
                                nonBotCount++
                            }
                        }
                    }
                    "合計 ${botCount + nonBotCount} / ボット $botCount / ユーザー $nonBotCount"
                }
                this.field("機能") {
                    guild.features
                        .joinToString(", ") { it.value }
                        .ifEmpty { "なし" }
                }
                this.field("サーバーブースト") {
                    "レベル ${guild.premiumTier.value} (${guild.premiumSubscriptionCount ?: 1}ブースト)"
                }
                this.field("チャンネル数") {
                    val channels = guild.channels.toList()
                    val categories = channels.count { it.type == ChannelType.GuildCategory }
                    val texts = channels.count { it.type == ChannelType.GuildText || it.type == ChannelType.GuildNews }
                    val voices =
                        channels.count { it.type == ChannelType.GuildVoice || it.type == ChannelType.GuildStageVoice }
                    val others =
                        channels.count { it.type != ChannelType.GuildCategory
                                && it.type != ChannelType.GuildText && it.type != ChannelType.GuildNews
                                && it.type != ChannelType.GuildVoice && it.type != ChannelType.GuildStageVoice
                        }
                    val ctv = categories + texts + voices + others
                    "C+T+V $ctv / カテゴリ $categories / テキスト $texts / ボイス $voices / その他 $others"
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("stats", "Shows stats of the guild.") {
            description(Locale.JAPANESE, "サーバーの情報を表示")

            dmPermission = false
        }
    }
}
