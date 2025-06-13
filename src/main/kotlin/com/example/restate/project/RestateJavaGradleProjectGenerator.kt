package com.example.restate.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import java.io.File

class RestateJavaGradleProjectGenerator : WebProjectTemplate<Any>() {
    override fun getName(): String = "Restate Java Gradle"

    override fun getDescription(): String = "Create a new Restate Java project with Gradle"

    override fun createPeer(): ProjectGeneratorPeer<Any> {
        return object : ProjectGeneratorPeer<Any> {
            override fun getComponent(): JComponent = JPanel()

            override fun getSettings(): Any = Any()

            override fun validate(): com.intellij.openapi.ui.ValidationInfo? = null

            override fun isBackgroundJobRunning(): Boolean = false

            override fun addSettingsListener(listener: ProjectGeneratorPeer.SettingsListener) {}

            override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {}
        }
    }

    override fun getLogo(): Icon? {
        return AllIcons.Plugins.PluginLogo
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: Any, module: Module) {
        // Copy template files from resources to the project directory
        val templatePath = "/projectTemplates/java-gradle"
        val targetDir = File(baseDir.path)

        try {
            // Copy all template files using the class loader
            copyTemplateResources(templatePath, targetDir)

            // Refresh the VFS to see the new files
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)
        } catch (e: Exception) {
            // Log error
            com.intellij.openapi.diagnostic.Logger.getInstance(RestateJavaGradleProjectGenerator::class.java)
                .error("Failed to copy template files", e)
        }
    }

    private fun copyTemplateResources(templatePath: String, targetDir: File) {
        val classLoader = RestateJavaGradleProjectGenerator::class.java.classLoader

        // List of known files in the template
        val templateFiles = listOf(
            "build.gradle.kts",
            "README.md",
            ".gitignore",
            "src/main/java/my/example/Greeter.java",
            "src/main/resources/log4j2.properties",
            "src/test/java/my/example/GreeterTest.java"
        )

        for (relativePath in templateFiles) {
            val resourcePath = "$templatePath/$relativePath"
            val inputStream = classLoader.getResourceAsStream(resourcePath.removePrefix("/"))

            if (inputStream != null) {
                val outputFile = File(targetDir, relativePath)

                // Create parent directories if they don't exist
                outputFile.parentFile.mkdirs()

                // Copy the resource to the output file
                outputFile.outputStream().use { outputStream ->
                    inputStream.use { input ->
                        input.copyTo(outputStream)
                    }
                }

                // Make gradlew executable
                if (relativePath == "gradlew") {
                    outputFile.setExecutable(true)
                }
            } else {
                com.intellij.openapi.diagnostic.Logger.getInstance(RestateJavaGradleProjectGenerator::class.java)
                    .warn("Resource not found: $resourcePath")
            }
        }
    }

}
