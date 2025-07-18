plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.1.0"
  id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "dev.restate"
version = "0.0.1"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
  intellijPlatform {
    create("IC", "2025.1")
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
  }

  // GitHub API for Java
  implementation("org.kohsuke:github-api:1.315")

  // Restate Admin API
  implementation("dev.restate:admin-client:2.1.0")

  // Apache Commons Compression for tar.xz extraction
  implementation("org.apache.commons:commons-compress:1.26.0")
  // XZ for Java for LZMA/XZ compression support
  implementation("org.tukaani:xz:1.9")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "251"
    }

    changeNotes = """
      Initial version
    """.trimIndent()
  }
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
  }
}
