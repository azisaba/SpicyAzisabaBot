package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.int
import dev.kord.rest.builder.interaction.string
import net.azisaba.gravenbuilder.ProjectType
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optString

object CustomBuildCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean =
        !System.getenv("DOCKER_HOST").isNullOrBlank()

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val url = interaction.optString("url")!!
        val image = interaction.optString("image")!!
        val command = interaction.optString("command")!!
        val artifactGlob = interaction.optString("artifact-glob") ?: "**.jar"
        val timeout = interaction.optLong("timeout") ?: 10L
        val projectType = ProjectType.withCustomImageCmd(image, "bash", "-c", command)
        BuildCommand.build(interaction, url, projectType, artifactGlob, timeout)
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("custom-build", "Builds a project using provided docker image") {
            description(Locale.JAPANESE, "指定されたDockerイメージを使用してプロジェクトをビルド")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            string("image", "The image to use") {
                description(Locale.JAPANESE, "使用するDockerイメージ")

                required = true
            }
            string("command", "Command line") {
                description(Locale.JAPANESE, "実行するコマンド")

                required = true
            }
            string("url", "URL of the git repository") {
                description(Locale.JAPANESE, "GitリポジトリのURL")

                required = true
            }
            string("artifact-glob", "Artifact glob (Default: **.jar)") {
                description(Locale.JAPANESE, "アップロードするファイルのglob (デフォルト: **.jar)")

                required = false
            }
            int("timeout", "Timeout in minutes (Default: 10)") {
                description(Locale.JAPANESE, "タイムアウトまでの時間(分) (デフォルト: 10)")

                required = false
                minValue = 1
            }
        }
    }
}
