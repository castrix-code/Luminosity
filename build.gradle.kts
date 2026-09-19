plugins {
    java
}

group = "com.chunx"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Build against a different Paper with: gradlew build -PpaperVersion=1.21.11-R0.1-SNAPSHOT
    val paperVersion = providers.gradleProperty("paperVersion").getOrElse("1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

