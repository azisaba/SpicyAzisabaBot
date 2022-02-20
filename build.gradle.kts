plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "net.azisaba"
version = "1.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.acrylicstyle.xyz/repository/maven-public/") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("xyz.acrylicstyle.util:common:0.16.5")
    implementation("dev.kord:kord-core:0.8.0-M8")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.3")
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
                "Main-Class" to "net.azisaba.spicyAzisabaBot.MainKt",
            )
        }
        archiveFileName.set("SpicyAzisabaBot.jar")
    }
}
