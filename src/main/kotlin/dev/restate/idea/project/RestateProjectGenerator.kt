package dev.restate.idea.project

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.panel
import dev.restate.idea.RestateIcons
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.util.jar.JarFile
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel


class RestateProjectGenerator : WebProjectTemplate<RestateProjectGenerator.RestateTemplateType>() {
  enum class RestateTemplateType(val displayName: String, val templatePath: String) {
    JAVA_GRADLE("Java (Gradle)", "/projectTemplates/java-gradle"),
    JAVA_MAVEN("Java (Maven)", "/projectTemplates/java-maven"),
    KOTLIN("Kotlin", "/projectTemplates/kotlin")
  }

  override fun getIcon() = RestateIcons.ProjectGenerator

  override fun getName() = "Restate"

  override fun getDescription(): String = "Create a new Restate Java project with Gradle"

  override fun createPeer(): ProjectGeneratorPeer<RestateTemplateType> {
    return object : ProjectGeneratorPeer<RestateTemplateType> {
      private lateinit var chooser: SegmentedButton<RestateTemplateType>
      private val settingsPanel = panel {
        row {
          chooser = segmentedButton(RestateTemplateType.entries) {
            text = it.displayName
          }.also {
            it.selectedItem = RestateTemplateType.JAVA_GRADLE
          }
        }
      }

      override fun getComponent(): JComponent = JPanel()

      override fun getSettings(): RestateTemplateType = chooser.selectedItem!!

      override fun validate(): ValidationInfo? = null

      override fun isBackgroundJobRunning(): Boolean = false

      override fun addSettingsListener(listener: ProjectGeneratorPeer.SettingsListener) {}

      override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsField("Template", settingsPanel)
      }
    }
  }

  override fun getLogo(): Icon? {
    return AllIcons.Plugins.PluginLogo
  }

  companion object {
    private val LOG = Logger.getInstance(RestateProjectGenerator::class.java)
  }

  override fun generateProject(project: Project, baseDir: VirtualFile, settings: RestateTemplateType, module: Module) {
    // Copy template files from resources to the project directory
    val templatePath = settings.templatePath
    val targetDir = File(baseDir.path)

    try {
      // Copy all template files using the class loader
      copyTemplateResources(templatePath, targetDir)

      // Refresh the VFS to see the new files
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)
    } catch (e: Exception) {
      // Log error
      LOG.error("Failed to copy template files", e)
    }
  }

  private fun copyTemplateResources(templatePath: String, targetDir: File) {
    val classLoader = RestateProjectGenerator::class.java.classLoader

    // Dynamically discover all files in the template directory
    val templateFiles =
      listResourceFiles(RestateProjectGenerator::class.java.classLoader, templatePath.removePrefix("/"))

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
        LOG.warn("Resource not found: $resourcePath")
      }
    }
  }

  private fun listResourceFiles(classLoader: ClassLoader, path: String): List<String> {
    val dirURL = classLoader.getResource(path) ?: return emptyList()
    if (dirURL.protocol.equals("file")) {
      /* A file path: use Files.walk for recursion */
      val directory = File(dirURL.toURI())
      return try {
        Files.walk(directory.toPath())
          .filter { Files.isRegularFile(it) }
          .map { directory.toPath().relativize(it).toString().replace('\\', '/') }
          .toList()
      } catch (e: Exception) {
        emptyList()
      }
    }

    if (dirURL.protocol.equals("jar")) {
      /* A JAR path */
      val jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")) //strip out only the JAR file
      val jar = JarFile(URLDecoder.decode(jarPath, "UTF-8"))
      val entries = jar.entries().toList().listIterator() //gives ALL entries in jar
      val result = mutableListOf<String>()

      while (entries.hasNext()) {
        val entry = entries.next()
        if (entry.isDirectory) {
          continue
        }
        val name = entry.name
        if (name.startsWith(path)) { //filter according to the path
          // Get the relative path from the base directory
          val entry = name.substring(path.length)
          if (entry.isNotEmpty()) {
            // Include all entries (not just top-level ones)
            result.add(entry.removePrefix("/"))
          }
        }
      }
      return result.toList()
    }

    throw UnsupportedOperationException("Cannot list files for URL $dirURL")
  }
}
