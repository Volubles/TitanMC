import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Copy

plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.voluble"
version = "1.0-SNAPSHOT"

val minecraftVersion = "1.21.11"
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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.15")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:2.11.6")

    // This is installed locally by the MichelleLib Maven project. Its provided
    // dependencies belong to the server, so only MichelleLib itself is shaded.
    implementation("io.voluble.michelle:michelle-lib:1.0.0-SNAPSHOT") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testImplementation("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
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
