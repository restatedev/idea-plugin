package com.example.restate.wizard

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/**
 * Step for adding Restate-specific assets to a new project.
 * This step is executed after the language-specific setup (Java or Kotlin).
 */
class RestateAssetStep(private val parentStep: NewProjectWizardStep) : AbstractNewProjectWizardStep(parentStep) {

  override fun setupProject(project: Project) {
    // Add Restate-specific files and configurations
    addDockerfile(project)
    addDockerCompose(project)
    addReadme(project)

    // Add language-specific Restate code
    val language = parentStep.data.getUserData(RestateWizardData.KEY)!!.language
    if (language == RestateWizardData.Language.Kotlin) {
      addKotlinSampleCode(project)
    } else {
      addJavaSampleCode(project)
    }
  }

  private fun addDockerfile(project: Project) {
    val content = """
            FROM ghcr.io/restatedev/restate:latest

            # Add your custom configuration here
        """.trimIndent()

    createFileInProject(project, "Dockerfile", content)
  }

  private fun addDockerCompose(project: Project) {
    val content = """
            version: '3'
            services:
              restate:
                image: ghcr.io/restatedev/restate:latest
                ports:
                  - "8080:8080"  # UI/Admin
                  - "9070:9070"  # Ingress
                  - "9071:9071"  # Invocation
                volumes:
                  - ./data:/data
        """.trimIndent()

    createFileInProject(project, "docker-compose.yml", content)
  }

  private fun addReadme(project: Project) {
    val content = """
            # Restate Project

            This is a new Restate project created with the IntelliJ IDEA Restate plugin.

            ## Getting Started

            1. Start the Restate runtime using Docker:
               ```
               docker-compose up -d
               ```

            2. Build and run your service

            3. Open the Restate UI at http://localhost:8080
        """.trimIndent()

    createFileInProject(project, "README.md", content)
  }

  private fun addKotlinSampleCode(project: Project) {
    val packageName = getBasePackage()
    val packagePath = packageName.replace(".", "/")

    val serviceContent = """
            package $packageName

            import dev.restate.sdk.kotlin.annotation.Handler
            import dev.restate.sdk.kotlin.annotation.Service

            @Service
            class GreeterService {
                @Handler
                fun greet(name: String): String {
                    return "Hello, ${'$'}name from Restate!"
                }
            }
        """.trimIndent()

    val mainContent = """
            package $packageName

            import dev.restate.sdk.kotlin.Runtime

            fun main() {
                val runtime = Runtime.forService(GreeterService::class)
                runtime.start()
            }
        """.trimIndent()

    val srcDir = project.basePath?.let { VfsUtil.findFileByIoFile(java.io.File("$it/src/main/kotlin"), true) }
    if (srcDir != null) {
      val packageDir = createDirectories(srcDir, packagePath)
      createFile(packageDir, "GreeterService.kt", serviceContent)
      createFile(packageDir, "Main.kt", mainContent)
    }
  }

  private fun addJavaSampleCode(project: Project) {
    val packageName = getBasePackage()
    val packagePath = packageName.replace(".", "/")

    val serviceContent = """
            package $packageName;

            import dev.restate.sdk.annotation.Handler;
            import dev.restate.sdk.annotation.Service;

            @Service
            public class GreeterService {
                @Handler
                public String greet(String name) {
                    return "Hello, " + name + " from Restate!";
                }
            }
        """.trimIndent()

    val mainContent = """
            package $packageName;

            import dev.restate.sdk.Runtime;

            public class Main {
                public static void main(String[] args) {
                    Runtime runtime = Runtime.forService(GreeterService.class);
                    runtime.start();
                }
            }
        """.trimIndent()

    val srcDir = project.basePath?.let { VfsUtil.findFileByIoFile(java.io.File("$it/src/main/java"), true) }
    if (srcDir != null) {
      val packageDir = createDirectories(srcDir, packagePath)
      createFile(packageDir, "GreeterService.java", serviceContent)
      createFile(packageDir, "Main.java", mainContent)
    }
  }

  private fun getBasePackage(): String {
    // Get the base package from the wizard data or use a default
    return "com.example.restate"
  }

  private fun createFileInProject(project: Project, fileName: String, content: String) {
    project.basePath?.let {
      try {
        val baseDir = VfsUtil.findFileByIoFile(java.io.File(it), true)
        baseDir?.let { dir ->
          createFile(dir, fileName, content)
        }
      } catch (e: IOException) {
        // Log error
      }
    }
  }

  private fun createDirectories(parent: VirtualFile, path: String): VirtualFile {
    var current = parent
    for (part in path.split("/")) {
      if (part.isNotEmpty()) {
        val child = current.findChild(part) ?: current.createChildDirectory(this, part)
        current = child
      }
    }
    return current
  }

  private fun createFile(directory: VirtualFile, fileName: String, content: String) {
    try {
      val file = directory.findChild(fileName) ?: directory.createChildData(this, fileName)
      VfsUtil.saveText(file, content)
    } catch (e: IOException) {
      // Log error
    }
  }
}
