package net.azisaba.spicyazisababot.messages

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.azisaba.gravenbuilder.GravenBuilder
import net.azisaba.gravenbuilder.GravenBuilderConfig
import net.azisaba.gravenbuilder.ProjectType
import net.azisaba.spicyazisababot.util.Constant
import net.azisaba.spicyazisababot.util.Util
import net.azisaba.spicyazisababot.util.Util.getEnvOrThrow
import net.azisaba.spicyazisababot.util.Util.toDiscord
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GitHub
import org.mariadb.jdbc.MariaDbBlob
import xyz.acrylicstyle.util.ArgumentParserBuilder
import xyz.acrylicstyle.util.InvalidArgumentException
import java.io.File
import java.nio.file.FileSystems
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val PULL_REQUEST_PATTERN = "^https://github\\.com/([^/]+?)/([^/]+?)/pull/(\\d+)/?\$".toRegex()
val PULL_COMMIT_PATTERN = "^https://github\\.com/([^/]+?)/([^/]+?)/pull/(\\d+)/commits/([a-zA-Z\\d]+)/?\$".toRegex()
val COMMIT_PATTERN = "^https://github\\.com/([^/]+?)/([^/]+?)/commit/([a-zA-Z\\d]+)/?\$".toRegex()
val REPO_WITH_BRANCH_PATTERN = "^https://github\\.com/([^/]+?)/([^/]+?)/tree/([^/]+?)/?\$".toRegex()
val REPO_PATTERN = "^https://github\\.com/([^/]+?)/([^/]+?)/?\$".toRegex()

@Suppress("SqlNoDataSourceInspection")
object BuildMessageHandler: MessageHandler {
    private val counter = java.util.concurrent.atomic.AtomicLong(0)

    override suspend fun canProcess(message: Message): Boolean =
        !System.getenv("DOCKER_HOST").isNullOrBlank() && message.author?.isBot == false && message.content.split(" ")[0] == "/build"

    override suspend fun handle(message: Message) {
        if (message.getAuthorAsMember()?.getPermissions()?.contains(Permission.Administrator) != true &&
            message.getAuthorAsMember()?.roleIds?.contains(Constant.BUILDER_ROLE) != true) {
            return
        }
        getEnvOrThrow("MARIADB_HOST")
        getEnvOrThrow("MARIADB_NAME")
        getEnvOrThrow("MARIADB_USERNAME")
        getEnvOrThrow("MARIADB_PASSWORD")
        val args = try {
            ArgumentParserBuilder.builder()
                .parseOptionsWithoutDash()
                .create()
                .parse(message.content.split(" ").drop(1).joinToString(" "))
        } catch (e: InvalidArgumentException) {
            message.reply { content = e.toDiscord() }
            return
        }
        if (args.unhandledArguments().size == 0) {
            message.reply { content = "`/build [--project-type=gradle|maven [--prepend-cmd=] [--append-cmd=]] [--artifact-glob=**.jar] [--timeout=N_in_minutes] <github url>`" }
            return
        }
        var projectType = args.getArgument("project-type")?.let {
            if (it.equals("gradle", true)) {
                ProjectType.GRADLE
            } else if (it.equals("maven", true)) {
                ProjectType.MAVEN
            } else {
                null
            }
        }
        if (projectType != null && args.containsArgumentKey("append-cmd")) {
            projectType = ProjectType.withCustomImageCmd(projectType.image, *projectType.cmd.dropLast(1).toTypedArray(), projectType.cmd.last() + " " + args.getArgument("append-cmd"))
        }
        if (projectType != null && args.containsArgumentKey("prepend-cmd")) {
            projectType = ProjectType.withCustomImageCmd(projectType.image, *projectType.cmd.dropLast(1).toTypedArray(), args.getArgument("prepend-cmd") + " " + projectType.cmd.last())
        }
        val artifactGlob = args.getArgument("artifact-glob") ?: "**.jar"
        val timeout = args.getArgument("timeout")?.toLongOrNull() ?: 10L
        var output = ""
        output += "[SpicyAzisabaBot] Project type override: $projectType\n"
        output += "[SpicyAzisabaBot] Artifact glob: $artifactGlob\n"
        output += "[SpicyAzisabaBot] URL: ${args.unhandledArguments()[0]}\n"
        output += "[SpicyAzisabaBot] Timeout: $timeout minutes\n"
        val (output2, repoDir) = try {
            cloneRepository(output, args.unhandledArguments()[0])
        } catch (e: Exception) {
            message.reply { content = "Failed to clone repository: ${e.message}" }
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
        val msg = message.reply {
            content = ":hourglass: ビルド中... (type: `$projectType`)"
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
                                        trimOutput(output)
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
                .dockerHost(getEnvOrThrow("DOCKER_HOST"))
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
                msg.edit {
                    content = ":white_check_mark: ビルド完了\nビルドログ: $buildLogUrl$artifactUrlsInContent"
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
                    Thread.sleep(1000)
                    msg.edit {
                        content = ":x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s\nビルドログ: $buildLogUrl"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.IO) {
                latch.await(10, TimeUnit.SECONDS)
            }
            msg.edit {
                content = ":x: ビルド失敗\n経過時間: ${(System.currentTimeMillis() - startedAt) / 1000}s"
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
        if (COMMIT_PATTERN.matches(githubUrl)) {
            val res = COMMIT_PATTERN.find(githubUrl)!!
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
        } else if (PULL_REQUEST_PATTERN.matches(githubUrl)) {
            val res = PULL_REQUEST_PATTERN.find(githubUrl)!!
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
        } else if (PULL_COMMIT_PATTERN.matches(githubUrl)) {
            val res = PULL_COMMIT_PATTERN.find(githubUrl)!!
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
        } else if (REPO_WITH_BRANCH_PATTERN.matches(githubUrl)) {
            val res = REPO_WITH_BRANCH_PATTERN.find(githubUrl)!!
            val (_, owner, repo, branch) = res.groupValues
            if (!dir.mkdir()) {
                error("Failed to create directory: ${dir.absolutePath}")
            }
            val url = "https://github.com/$owner/$repo"
            output += "[SpicyAzisabaBot] Cloning repository from $url with branch: $branch\n"
            Git.cloneRepository().setDirectory(dir).setURI(url).setBranch(branch).call().close()
            return Pair(output, dir)
        } else if (REPO_PATTERN.matches(githubUrl)) {
            val res = REPO_PATTERN.find(githubUrl)!!
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

    private fun trimOutput(output: String): String {
        var chars = 0
        val lines = mutableListOf<String>()
        output.lines().reversed().forEach { line ->
            if (chars + line.length > 1600) {
                return lines.reversed().joinToString("\n")
            }
            chars += line.length
            lines.add(line)
        }
        return lines.reversed().joinToString("\n")
    }

    private fun getGitHub(): GitHub =
        System.getenv("GITHUB_TOKEN").let {
            if (it == null) {
                GitHub.connectAnonymously()
            } else {
                GitHub.connect(null, it)
            }
        }
}
