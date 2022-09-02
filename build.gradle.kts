plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "net.azisaba"
version = "2.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://repo.acrylicstyle.xyz/repository/maven-public/") }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

dependencies {
    implementation(kotlin("stdlib"))
    implementation("xyz.acrylicstyle.util:http:0.16.5")
    implementation("dev.kord:kord-core:0.8.0-M16")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.6")
    implementation("com.github.AzisabaNetwork:GravenBuilder:6d03d0aae6")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.2.0.202206071550-r")
    implementation("org.kohsuke:github-api:1.308")
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }

    shadowJar {
        manifest {
            attributes(
                "Main-Class" to "net.azisaba.spicyazisababot.MainKt",
            )
        }
        archiveFileName.set("SpicyAzisabaBot.jar")
    }
}
