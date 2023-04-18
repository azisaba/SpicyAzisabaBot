plugins {
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.azisaba"
version = "2.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io/") }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

dependencies {
    implementation(kotlin("stdlib"))
    implementation("dev.kord:kord-core:0.8.2")
    implementation("org.slf4j:slf4j-simple:2.0.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.6")
    implementation("com.github.AzisabaNetwork:GravenBuilder:6d03d0aae6")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.2.0.202206071550-r")
    implementation("org.kohsuke:github-api:1.308")
    implementation("com.charleskorn.kaml:kaml:0.53.0")
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
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
