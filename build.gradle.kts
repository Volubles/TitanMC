import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Copy

plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
    id("io.github.drownek.paperwright") version "1.3.2"
}

group = "com.voluble"
version = "1.0-SNAPSHOT"

val targetMinecraftVersion = "1.21.11"
val serverDirectory = providers.gradleProperty("serverDirectory")
    .map { file(it) }
    .orElse(layout.projectDirectory.dir("run").asFile)

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "codemcReleases"
    }
    maven("https://repo.codemc.io/repository/maven-snapshots/") {
        name = "codemcSnapshots"
    }
    maven("https://maven.typewritermc.com/external") {
        name = "typewriterExternal"
    }
    maven("https://repo.nexomc.com/releases") {
        name = "nexo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$targetMinecraftVersion-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.10.2")
    compileOnly("com.github.retrooper:packetevents-api:2.11.1")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.1")
    compileOnly("com.nexomc:nexo:1.24.0")

    // This is installed locally by the MichelleLib Maven project. Its provided
    // dependencies belong to the server, so only MichelleLib itself is shaded.
    implementation("io.voluble.michelle:michelle-lib:1.0.0-SNAPSHOT") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("me.tofaa2:spigot:3.2.0-SNAPSHOT") {
        exclude(group = "com.github.retrooper", module = "packetevents-api")
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testImplementation("io.papermc.paper:paper-api:$targetMinecraftVersion-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

paperwright {
    minecraftVersion.set(targetMinecraftVersion)
    runDir.set(layout.projectDirectory.dir("run/paperwright"))
    testsDir.set(layout.projectDirectory.dir("src/test/e2e"))
    acceptEula.set(true)
    jvmArgs.set(listOf("-Xmx2G"))
    downloadPlugins {
        url("https://hangarcdn.papermc.io/plugins/EngineHub/WorldEdit/versions/7.4.2/PAPER/worldedit-bukkit-7.4.2.jar")
    }
    writeFiles {
        file("plugins/TitanMC/config.yml", """
            economy:
              enabled: false

            protection:
              enabled: true
              bypass-permission: titanmc.protection.bypass
              protected-worlds:
                - world
        """.trimIndent())
    }
}

tasks.matching { it.name.startsWith("paperwright") }.configureEach {
    notCompatibleWithConfigurationCache("Paperwright 1.3.2 captures Gradle project state in its tasks")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("regionBenchmark") {
    group = "verification"
    description = "Runs the deterministic Titan Region Engine lookup benchmark."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.voluble.titanMC.regions.benchmark.RegionIndexBenchmark"
}

tasks.register<JavaExec>("mineTemplateBenchmark") {
    group = "verification"
    description = "Benchmarks Titan mine template compression and decoding."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.voluble.titanMC.mines.template.MineTemplateFormatBenchmark"
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    mergeServiceFiles()
    relocate(
        "io.voluble.michellelib",
        "com.voluble.titanMC.libs.michellelib",
    )
}

tasks.jar {
    archiveClassifier = "plain"
}

val deployPlugin by tasks.registering(Copy::class) {
    group = "development"
    description = "Builds the shaded plugin and copies it to the development server."
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(serverDirectory.map { File(it, "plugins") })
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(deployPlugin)
}
