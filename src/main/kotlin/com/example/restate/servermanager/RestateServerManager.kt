package com.example.restate.servermanager

import com.example.restate.RestateNotifications.showNotification
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.messages.MessageBus
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.kohsuke.github.GitHubBuilder
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * Manages the Restate server binary, including downloading, updating, and running it.
 */
class RestateServerManager(private val project: Project) {
  private var serverRunning = false
  private var consoleView = createConsoleView()
  private val messageBus: MessageBus = project.messageBus

  companion object {
    val RESTATE_SERVER_MANAGER_KEY = Key.create<RestateServerManager>("restateServerManager")
    private val LOG = Logger.getInstance(RestateServerManager::class.java)

    // GitHub repository information
    private val REPO_OWNER = "restatedev"
    private val REPO_NAME = "restate"
  }

  /**
   * Notifies all subscribers that the server has started.
   */
  private fun notifyServerStarted() {
    messageBus.syncPublisher(RestateServerTopic.TOPIC).onServerStarted()
  }

  /**
   * Notifies all subscribers that the server has stopped.
   */
  private fun notifyServerStopped() {
    messageBus.syncPublisher(RestateServerTopic.TOPIC).onServerStopped()
  }

  // Binary paths
  private val RESTATE_BINARY_DIR = Paths.get(PathManager.getSystemPath(), "restate-plugin")
  private val RESTATE_BINARY_PATH: Path
    get() = RESTATE_BINARY_DIR.resolve("restate-server")

  init {
    // Create binary directory if it doesn't exist
    Files.createDirectories(RESTATE_BINARY_DIR)
  }

  /**
   * Downloads the latest release of the Restate server binary.
   *
   * @return The path to the downloaded binary
   * @throws Exception if the download fails
   */
  fun downloadLatestRelease(project: Project): Path {
    LOG.info("Downloading latest Restate release")

    try {
      // Connect to GitHub API
      val github = GitHubBuilder().build()
      val repository = github.getRepository("$REPO_OWNER/$REPO_NAME")

      // Get the latest release
      val latestRelease = repository.listReleases().iterator().next()
      LOG.info("Latest release: ${latestRelease.name}")

      // Find the appropriate asset for the current platform
      val os = getOperatingSystem()
      val arch = getArchitecture()
      LOG.info("Looking for asset for OS: $os, Architecture: $arch")

      val asset = latestRelease.listAssets().find {
        it.name.contains("restate-server") &&
            it.name.contains(os) &&
            it.name.contains(arch)
      } ?: throw Exception("Could not find appropriate release asset for your platform")

      // Now use Intellij Platform downloader.
      val downloaderService = DownloadableFileService.getInstance()
      val downloadFileDescription = downloaderService.createFileDescription(
        asset.browserDownloadUrl,
        asset.name
      )
      val downloader = downloaderService.createDownloader(
        listOf(downloadFileDescription),
        "Restate Server"
      )

      LOG.info("Downloading Restate binary from: ${asset.browserDownloadUrl}")

      // Create a temporary directory for the download
      val tempDir = Files.createTempDirectory("restate-download")
      val result = downloader.downloadWithBackgroundProgress(tempDir.toCanonicalPath(), project)
        .get()
      val downloadedFile = result?.get(0)?.first?.inputStream ?: throw Exception("No file was downloaded!")

      try {
        // Extract the tar.xz file
        val extractDir = tempDir.resolve("extracted")
        Files.createDirectories(extractDir)

        LOG.info("Extracting archive using Apache Commons Compression...")

        // Create a chain of streams to handle the XZ and TAR formats
        var extractedBinary: Path? = null

        BufferedInputStream(downloadedFile).use { fileIn ->
          XZCompressorInputStream(fileIn).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
              var entry = tarIn.nextEntry
              while (entry != null) {
                // Check if this entry is the restate-server binary
                if (!entry.isDirectory && entry.name.endsWith("restate-server")) {
                  LOG.info("Found restate-server binary in archive: ${entry.name}")

                  // Create the target path
                  val entryPath = extractDir.resolve("restate-server")

                  // Create parent directories if they don't exist
                  Files.createDirectories(entryPath.parent)

                  // Copy the file content
                  Files.copy(tarIn, entryPath, StandardCopyOption.REPLACE_EXISTING)

                  // Save the path to the extracted binary
                  extractedBinary = entryPath

                  // We found what we need, no need to continue
                  break
                } else {
                  LOG.info("Skipping archive entry: ${entry.name}")
                }

                entry = tarIn.nextEntry
              }
            }
          }
        }

        LOG.info("Archive extraction completed")

        // Check if we found and extracted the binary
        if (extractedBinary == null) {
          throw Exception("Could not find restate-server binary in extracted archive")
        }

        LOG.info("Found extracted binary at: $extractedBinary")

        // Copy the binary to the final location
        Files.copy(extractedBinary, RESTATE_BINARY_PATH, StandardCopyOption.REPLACE_EXISTING)

        // Make the binary executable
        makeExecutable(RESTATE_BINARY_PATH)

        LOG.info("Successfully installed Restate binary to: $RESTATE_BINARY_PATH")
        return RESTATE_BINARY_PATH
      } finally {
        // Clean up temporary files
        try {
          Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
        } catch (e: Exception) {
          LOG.info("Failed to clean up temporary files", e)
        }
      }
    } catch (e: Exception) {
      LOG.error("Error downloading Restate release", e)
      throw e
    }
  }

  /**
   * Checks if the binary exists and is up to date.
   *
   * @return true if the binary needs to be updated, false otherwise
   */
  fun shouldCheckForUpdates(): Boolean {
    // Check for updates once a day
    val file = RESTATE_BINARY_PATH.toFile()
    if (!file.exists()) return true

    val lastModified = file.lastModified()
    val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

    return lastModified < oneDayAgo
  }

  /**
   * Gets the path to the Restate server binary.
   *
   * @return The path to the binary
   */
  fun getBinaryPath(): Path {
    return RESTATE_BINARY_PATH
  }

  /**
   * Makes a file executable.
   *
   * @param path The path to the file
   * @throws Exception if the operation fails
   */
  private fun makeExecutable(path: Path) {
    try {
      val file = path.toFile()
      if (!file.canExecute()) {
        // Try using PosixFilePermission if available
        try {
          val permissions = Files.getPosixFilePermissions(path).toMutableSet()
          permissions.add(PosixFilePermission.OWNER_EXECUTE)
          Files.setPosixFilePermissions(path, permissions)
        } catch (e: UnsupportedOperationException) {
          // Fallback for non-POSIX systems (e.g., Windows)
          file.setExecutable(true)
        }
      }
    } catch (e: Exception) {
      LOG.error("Failed to make binary executable", e)
      throw e
    }
  }

  /**
   * Gets the operating system name in a format compatible with Restate's release assets.
   *
   * @return The operating system name
   * @throws Exception if the operating system is not supported
   */
  private fun getOperatingSystem(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
      osName.contains("linux") -> "linux"
      osName.contains("mac") || osName.contains("darwin") -> "darwin"
      osName.contains("windows") -> "windows"
      else -> throw Exception("Unsupported operating system: $osName")
    }
  }

  /**
   * Gets the architecture name in a format compatible with Restate's release assets.
   *
   * @return The architecture name
   * @throws Exception if the architecture is not supported
   */
  private fun getArchitecture(): String {
    val arch = System.getProperty("os.arch").lowercase()
    return when {
      arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
      arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
      else -> throw Exception("Unsupported architecture: $arch")
    }
  }

  /**
   * Creates a console view for displaying server output.
   */
  private fun createConsoleView(): ConsoleView {
    // Create a ConsoleViewImpl with ANSI color support enabled
    return (TextConsoleBuilderFactory.getInstance().createBuilder(project) as TextConsoleBuilderImpl)
      .also { it.isUsePredefinedMessageFilter = true }
      .console
  }

  /**
   * Shows the server output in the Run tool window.
   */
  private fun showInRunToolWindow(title: String, processHandler: OSProcessHandler) =

    WriteAction.computeAndWait<ConsoleView, Throwable> {
      // Create run content descriptor
      val contentDescriptor = RunContentDescriptor(
        null,
        processHandler,
        consoleView.component,
        title
      )

      // Show in Run tool window
      RunContentManager.getInstance(project).showRunContent(
        DefaultRunExecutor.getRunExecutorInstance(),
        contentDescriptor
      )

      consoleView
    }


  fun startServer() {
    ApplicationManager.getApplication().executeOnPooledThread {
      this.startServerInner()
    }
  }

  /**
   * Starts the Restate server.
   */
  fun startServerInner() {
    if (serverRunning) {
      return
    }
    try {
      LOG.info("Starting Restate server")

      // Create console view if it doesn't exist
      consoleView.clear()

      // Print initial message
      consoleView.print("Starting Restate server...\n", ConsoleViewContentType.NORMAL_OUTPUT)

      // Download the binary if it doesn't exist or if we want to check for updates
      val binaryPath = getBinaryPath()
      if (shouldCheckForUpdates()) {
        consoleView.print("Downloading latest Restate binary...\n", ConsoleViewContentType.NORMAL_OUTPUT)
        downloadLatestRelease(project)
        consoleView.print("Download completed.\n", ConsoleViewContentType.NORMAL_OUTPUT)
      }

      // Start the server process
      val runCmd = GeneralCommandLine(
        binaryPath.toString(),
      )

      val processHandler =
        ProcessHandlerFactory.getInstance()
          .createColoredProcessHandler(runCmd)
      if (processHandler is KillableProcessHandler) {
        processHandler.setShouldKillProcessSoftly(true)
      }

      // Show in Run tool window
      showInRunToolWindow("Restate Server", processHandler)

      // Attach process to console
      consoleView.attachToProcess(processHandler)

      // Add a process listener to monitor the server
      processHandler.addProcessListener(object : ProcessListener {
        override fun startNotified(event: ProcessEvent) {}

        override fun processTerminated(event: ProcessEvent) {
          LOG.info("Restate server process terminated with exit code: ${event.exitCode}")
          consoleView.print(
            "\nRestate server process terminated with exit code: ${event.exitCode}\n",
            ConsoleViewContentType.SYSTEM_OUTPUT
          )

          serverRunning = false
          notifyServerStopped()
        }

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          // Detect when the server is ready
          val text = event.text
          if (text.contains("Server listening")) {
            LOG.info("Restate server is ready")
            notifyServerStarted()
          }
        }
      })

      // Start the process
      processHandler.startNotify()

      serverRunning = true
    } catch (e: Exception) {
      LOG.error("Error starting Restate server", e)
      serverRunning = false

      // Log the error
      LOG.error("Failed to start Restate server: ${e.message}")

      // Display error in the console
      consoleView.print(
        "\nERROR: Failed to start Restate server: ${e.message}\n",
        ConsoleViewContentType.ERROR_OUTPUT
      )
      // Display also notification
      showNotification(
        project,
        "Restate-server startup Failed",
        "Error when trying to startup restate-server: ${e.message}",
        NotificationType.ERROR
      )
    }
  }
}
