package net.azisaba.spicyazisababot.commands

import dev.kord.common.Locale
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.kordLogger
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.azisaba.gravenbuilder.GravenBuilder
import net.azisaba.gravenbuilder.GravenBuilderConfig
import net.azisaba.gravenbuilder.ProjectType
import net.azisaba.spicyazisababot.config.secret.BotSecretConfig
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.optLong
import net.azisaba.spicyazisababot.util.Util.optString
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GitHub
import org.mariadb.jdbc.MariaDbBlob
import java.io.File
import java.nio.file.FileSystems
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object BuildCommand : CommandHandler {
    private val counter = java.util.concurrent.atomic.AtomicLong(0)
    private val pullRequestPattern = "^https://github\\.com/([^/]+?)/([^/]+?)/pull/(\\d+)/?\$".toRegex()
    private val pullCommitPattern = "^https://github\\.com/([^/]+?)/([^/]+?)/pull/(\\d+)/commits/([a-zA-Z\\d]+)/?\$".toRegex()
    private val commitPattern = "^https://github\\.com/([^/]+?)/([^/]+?)/commit/([a-zA-Z\\d]+)/?\$".toRegex()
    private val repoWithBranchPattern = "^https://github\\.com/([^/]+?)/([^/]+?)/tree/(.+?)/?\$".toRegex()
    private val repoPattern = "^https://github\\.com/([^/]+?)/([^/]+?)/?\$".toRegex()

    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean =
        BotSecretConfig.config.dockerHost.isNotBlank()

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val url = interaction.optString("url")!!
        val type = interaction.optString("type")
        val prependCmd = interaction.optString("prepend-cmd")
        val appendCmd = interaction.optString("append-cmd")
        val artifactGlob = interaction.optString("artifact-glob") ?: "**.jar"
        val timeout = interaction.optLong("timeout") ?: 10
        var projectType = type?.let {
            if (it.equals("gradle", true)) {
                ProjectType.GRADLE
            } else if (it.equals("maven", true)) {
                ProjectType.MAVEN
            } else {
                null
            }
        }
        if (projectType != null && prependCmd != null) {
            projectType = ProjectType.withCustomImageCmd(projectType.image, *projectType.cmd.dropLast(1).toTypedArray(), prependCmd + " " + projectType.cmd.last())
        }
        if (projectType != null && appendCmd != null) {
            projectType = ProjectType.withCustomImageCmd(projectType.image, *projectType.cmd.dropLast(1).toTypedArray(), projectType.cmd.last() + " " + appendCmd)
        }
        build(interaction, url, projectType, artifactGlob, timeout)
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("build", "Builds a gradle or maven project") {
            description(Locale.JAPANESE, "GradleもしくはMavenのプロジェクトをビルド")

            dmPermission = false
            defaultMemberPermissions = Permissions()

            string("url", "URL of the git repository") {
                description(Locale.JAPANESE, "GitリポジトリのURL")

                required = true
            }
            string("type", "Project type") {
                description(Locale.JAPANESE, "プロジェクトの種類")

                required = false
                choice("Gradle", "gradle")
                choice("Maven", "maven")
            }
            string("prepend-cmd", "Command to prepend to the build command") {
                description(Locale.JAPANESE, "ビルドコマンドの先頭に追加するコマンド")

                required = false
            }
            string("append-cmd", "Command to append to the build command") {
                description(Locale.JAPANESE, "ビルドコマンドの末尾に追加するコマンド")

                required = false
            }
            string("artifact-glob", "Artifact glob (Default: **.jar)") {
                description(Locale.JAPANESE, "アップロードするファイルのglob (デフォルト: **.jar)")

                required = false
            }
            integer("timeout", "Timeout in minutes (Default: 10)") {
                description(Locale.JAPANESE, "タイムアウトまでの時間(分) (デフォルト: 10)")

                required = false
                minValue = 1
            }
        }
    }

    suspend fun build(
        interaction: ApplicationCommandInteraction,
        url: String,
        projectType: ProjectType?,
        artifactGlob: String = "**.jar",
        timeout: Long = 10,
    ) {
        val msg = interaction.respondPublic {
            content = ":hourglass: ビルド中... (type: `$projectType`)"
        }
        var output = ""
        output += "[SpicyAzisabaBot] Build triggered by ${interaction.user.id}\n"
        output += "[SpicyAzisabaBot] Project type override: $projectType\n"
        output += "[SpicyAzisabaBot] Artifact glob: $artifactGlob\n"
        output += "[SpicyAzisabaBot] URL: $url\n"
        output += "[SpicyAzisabaBot] Timeout: $timeout minutes\n"
        kordLogger.info("Initial build output:\n$output")
        val (output2, repoDir) = try {
            cloneRepository(output, url)
        } catch (e: Exception) {
            interaction.respondPublic { content = "Failed to clone repository: ${e.message}" }
            e.printStackTrace()
            return
        }
        output = output2
        output += "[SpicyAzisabaBot] Cloned repository to ${repoDir.absolutePath}\n"
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$artifactGlob")
        val repoPath = repoDir.toPath()
        val artifactPredicate: (File) -> Boolean = { matcher.matches(repoPath.relativize(it.toPath())) }
        repoDir.listFiles().let { files ->
            if (files?.isEmpty() != false) {
                output += "[SpicyAzisabaBot] warning: Cloned nothing"
            } else {
                output += "[SpicyAzisabaBot] Repository directory structure:\n"
                files.forEach { file ->
                    output += " - ${file.name}${if (file.isDirectory) "/" else ""}\n"
                }
            }
        }
        val startedAt = System.currentTimeMillis()
        val completed = java.util.concurrent.atomic.AtomicBoolean()
        val latch = CountDownLatch(1)
        try {
            Thread {
                while (!completed.get()) {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(if (System.currentTimeMillis() - startedAt < 60000) 3000 else 5000)
                        }
                        if (!completed.get()) {
                            msg.edit {
                                content =
                                    ":hourglass: ビルド中...\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s\n```\n${
                                        trimOutput(output, 1600)
                                    }\n```"
                            }
                            if (completed.get()) {
                                latch.countDown()
                            }
                        }
                    }
                }
                latch.countDown()
            }.start()
            val artifacts = GravenBuilderConfig()
                .dockerHost(BotSecretConfig.config.dockerHost)
                .onStdout { output += "$it\n" }
                .onStderr { output += "$it\n" }
                .onDebug { output += "[GravenBuilder] $it\n" }
                .timeout(timeout, TimeUnit.MINUTES)
                .isArtifact(artifactPredicate)
                .let { GravenBuilder(it) }
                .buildOn(repoDir, 17, projectType)
            // upload build log
            Util.getConnection().use { connection ->
                val artifactUrls = artifacts.map { file ->
                    output += "[SpicyAzisabaBot] Uploading artifact ${file.absolutePath}\n"
                    val artifactAttachmentId = "gb${System.currentTimeMillis()}-${counter.getAndIncrement()}"
                    val artifactFileName = file.name
                    if (file.length() > 1024 * 1024 * 100) {
                        output += "[SpicyAzisabaBot] Skipping large artifact (>100MB): ${file.absolutePath}\n"
                        return@map null
                    }
                    val artifactUrl =
                        "${System.getenv("MESSAGE_VIEWER_BASE_URL")}/attachments/$artifactAttachmentId/$artifactFileName"
                    val insertArtifact =
                        connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)")
                    insertArtifact.setString(1, "0") // message id
                    insertArtifact.setString(2, artifactAttachmentId)
                    insertArtifact.setString(3, artifactFileName)
                    insertArtifact.setString(4, artifactFileName)
                    insertArtifact.setBoolean(5, false)
                    insertArtifact.setBlob(6, MariaDbBlob(file.readBytes()))
                    insertArtifact.executeUpdate()
                    insertArtifact.close()
                    output += "[SpicyAzisabaBot] Uploaded artifact: $artifactUrl\n"
                    return@map artifactUrl
                }.filterNotNull()
                output += "[SpicyAzisabaBot] Completed build & upload in ${(System.currentTimeMillis() - startedAt) / 1000}s\n"
                output += "[SpicyAzisabaBot] Uploading build log\n"
                val buildLogAttachmentId = "gb${System.currentTimeMillis()}-${counter.getAndIncrement()}"
                val buildLogFileName = "_build-log.txt"
                val buildLogUrl =
                    "${System.getenv("MESSAGE_VIEWER_BASE_URL")}/attachments/$buildLogAttachmentId/$buildLogFileName"
                val insertBuildLog = connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)")
                insertBuildLog.setString(1, "0") // message id
                insertBuildLog.setString(2, buildLogAttachmentId)
                insertBuildLog.setString(3, buildLogFileName)
                insertBuildLog.setString(4, buildLogFileName)
                insertBuildLog.setBoolean(5, false)
                insertBuildLog.setBlob(6, MariaDbBlob(output.toByteArray()))
                insertBuildLog.executeUpdate()
                insertBuildLog.close()
                val artifactUrlsInContent = when (artifactUrls.size) {
                    1 -> "\nファイル: ${artifactUrls[0]}"
                    2 -> "\nファイル:\n- ${artifactUrls[0]}\n- ${artifactUrls[1]}"
                    else -> ""
                }
                latch.await(10, TimeUnit.SECONDS)
                msg.delete()
                /*
                msg.edit {
                    content = ":white_check_mark: ビルド完了\nビルドログ: $buildLogUrl$artifactUrlsInContent"
                }
                */
                interaction.channel.createMessage {
                    content = "<@${interaction.user.id}>\n:white_check_mark: ビルド完了\nビルドログ: $buildLogUrl$artifactUrlsInContent"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            output += "[SpicyAzisabaBot] Failed to build:\n"
            e.stackTraceToString().lines().forEach { output += "[SpicyAzisabaBot] $it\n" }
            try {
                Util.getConnection().use { connection ->
                    output += "[SpicyAzisabaBot] Uploading build log\n"
                    val buildLogAttachmentId = "gb${System.currentTimeMillis()}-${counter.getAndIncrement()}"
                    val buildLogFileName = "_build-log.txt"
                    val buildLogUrl = "${System.getenv("MESSAGE_VIEWER_BASE_URL")}/attachments/$buildLogAttachmentId/$buildLogFileName"
                    val insertBuildLog = connection.prepareStatement("INSERT INTO `attachments` VALUES (?, ?, ?, ?, ?, ?)")
                    insertBuildLog.setString(1, "0") // message id
                    insertBuildLog.setString(2, buildLogAttachmentId)
                    insertBuildLog.setString(3, buildLogFileName)
                    insertBuildLog.setString(4, buildLogFileName)
                    insertBuildLog.setBoolean(5, false)
                    insertBuildLog.setBlob(6, MariaDbBlob(output.toByteArray()))
                    insertBuildLog.executeUpdate()
                    insertBuildLog.close()
                    latch.await(10, TimeUnit.SECONDS)
                    msg.delete()
                    /*
                    msg.edit {
                        content = ":x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s\nビルドログ: $buildLogUrl"
                    }
                    */
                    interaction.channel.createMessage {
                        content = "<@${interaction.user.id}>\n:x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s\nビルドログ: $buildLogUrl"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.IO) {
                latch.await(10, TimeUnit.SECONDS)
            }
            msg.delete()
            /*
            msg.edit {
                content = ":x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s"
            }
            */
            interaction.channel.createMessage {
                content = "<@${interaction.user.id}>\n:x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s"
            }
        } finally {
            repoDir.deleteRecursively()
            completed.set(true)
        }
    }

    private fun cloneRepository(_output: String, githubUrl: String): Pair<String, File> {
        var output = _output
        val tmp = System.getProperty("java.io.tmpdir")!!
        val dir = File(tmp, "gb${System.currentTimeMillis()}")
        if (commitPattern.matches(githubUrl)) {
            val res = commitPattern.find(githubUrl)!!
            val (_, owner, repo, commit) = res.groupValues
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            val url = "https://github.com/$owner/$repo"
            output += "[SpicyAzisabaBot] Cloning repository from $url\n"
            Git.cloneRepository().setDirectory(dir).setURI(url).call().use { git ->
                output += "[SpicyAzisabaBot] Checking out commit $commit\n"
                git.checkout().setName(commit).call()
            }
            return Pair(output, dir)
        } else if (pullRequestPattern.matches(githubUrl)) {
            val res = pullRequestPattern.find(githubUrl)!!
            val (_, owner, repo, pullRequestS) = res.groupValues
            val pullRequest = pullRequestS.toInt()
            val head = getGitHub().getRepository("$owner/$repo").getPullRequest(pullRequest).head // base <- head
            val headRef = head.ref // branch name
            val headRepo = head.repository.fullName // owner/repo
            val newUrl = "https://github.com/$headRepo"
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            output += "[SpicyAzisabaBot] Cloning repository from $newUrl with branch: $headRef\n"
            Git.cloneRepository().setDirectory(dir).setURI(newUrl).setBranch(headRef).call().close()
            return Pair(output, dir)
        } else if (pullCommitPattern.matches(githubUrl)) {
            val res = pullCommitPattern.find(githubUrl)!!
            val (_, owner, repo, pullRequestS, commit) = res.groupValues
            val pullRequest = pullRequestS.toInt()
            val head = getGitHub().getRepository("$owner/$repo").getPullRequest(pullRequest).head // base <- head
            val headRepo = head.repository.fullName // owner/repo
            val newUrl = "https://github.com/$headRepo"
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            output += "[SpicyAzisabaBot] Cloning repository from $newUrl\n"
            Git.cloneRepository().setDirectory(dir).setURI(newUrl).call().use { git ->
                output += "[SpicyAzisabaBot] Checking out commit $commit\n"
                git.checkout().setName(commit).call()
            }
            return Pair(output, dir)
        } else if (repoWithBranchPattern.matches(githubUrl)) {
            val res = repoWithBranchPattern.find(githubUrl)!!
            val (_, owner, repo, branch) = res.groupValues
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            val url = "https://github.com/$owner/$repo"
            output += "[SpicyAzisabaBot] Cloning repository from $url with branch: $branch\n"
            Git.cloneRepository().setDirectory(dir).setURI(url).setBranch(branch).call().close()
            return Pair(output, dir)
        } else if (repoPattern.matches(githubUrl)) {
            val res = repoPattern.find(githubUrl)!!
            val (_, owner, repo) = res.groupValues
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            val url = "https://github.com/$owner/$repo"
            output += "[SpicyAzisabaBot] Cloning repository from $url\n"
            Git.cloneRepository().setDirectory(dir).setURI(url).call().close()
            return Pair(output, dir)
        } else {
            error("Invalid GitHub URL: $githubUrl")
        }
    }

    fun trimOutput(output: String, maxStringLength: Int = 1900, linePrefix: String = ""): String {
        var chars = 0
        val lines = mutableListOf<String>()
        output.lines().reversed().forEach { line ->
            if ((chars + linePrefix.length + line.length) > maxStringLength) {
                return lines.reversed().joinToString("\n")
            }
            chars += linePrefix.length + line.length
            lines.add(linePrefix + line)
        }
        return lines.reversed().joinToString("\n")
    }

    private fun getGitHub(): GitHub =
        BotSecretConfig.config.githubToken.let {
            if (it.isBlank()) {
                GitHub.connectAnonymously()
            } else {
                GitHub.connect(null, it)
            }
        }
}
