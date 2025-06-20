package com.example.restate.servermanager

import com.example.restate.RestateIcons
import com.example.restate.RestateNotifications.showNotification
import com.example.restate.runconfiguration.RestateExecutionListener
import com.example.restate.settings.RestateSettings
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
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.messages.MessageBus
import dev.restate.admin.api.DeploymentApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf
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
  private val messageBus: MessageBus = project.messageBus

  @Volatile
  private var startRequested = false

  companion object {
    val RESTATE_SERVER_MANAGER_KEY = Key.create<RestateServerManager>("restateServerManager")
    private val LOG = Logger.getInstance(RestateServerManager::class.java)

    // GitHub repository information
    private val REPO_OWNER = "restatedev"
    private val REPO_NAME = "restate"

    private val deploymentApiClient = DeploymentApi(ApiClient().setHost("127.0.0.1").setPort(9070))

    // --- Service Registration Logic ---
    fun registerRestateService() {
      val registerDeploymentRequest = RegisterDeploymentRequestAnyOf()
      registerDeploymentRequest.uri = "http://localhost:9080"
      registerDeploymentRequest.force = true

      val maxRetries = 3
      var retryCount = 0
      var lastException: Exception? = null

      while (retryCount < maxRetries) {
        try {
          deploymentApiClient.createDeployment(RegisterDeploymentRequest(registerDeploymentRequest))
          return // Success, exit the function
        } catch (e: Exception) {
          lastException = e
          LOG.info("Attempt ${retryCount + 1}/$maxRetries to register Restate service failed: ${e.message}")
          retryCount++

          if (retryCount < maxRetries) {
            // Exponential backoff: wait longer between each retry
            val delayMs = 100L * (1 shl retryCount)
            Thread.sleep(delayMs)
          }
        }
      }

      // If we get here, all retries failed
      throw lastException ?: RuntimeException("Failed to register Restate service after $maxRetries attempts")
    }
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
  private val RESTATE_PLUGIN_DIR = Paths.get(PathManager.getSystemPath(), "restate-plugin")
  private val RESTATE_SERVER_DOWNLOAD_PATH: Path
    get() = RESTATE_PLUGIN_DIR.resolve("restate-server")

  init {
    // Create binary directory if it doesn't exist
    Files.createDirectories(RESTATE_PLUGIN_DIR)
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
        Files.copy(extractedBinary, RESTATE_SERVER_DOWNLOAD_PATH, StandardCopyOption.REPLACE_EXISTING)

        // Make the binary executable
        makeExecutable(RESTATE_SERVER_DOWNLOAD_PATH)

        LOG.info("Successfully installed Restate binary to: $RESTATE_SERVER_DOWNLOAD_PATH")
        return RESTATE_SERVER_DOWNLOAD_PATH
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
    val file = RESTATE_SERVER_DOWNLOAD_PATH.toFile()
    if (!file.exists()) return true

    val lastModified = file.lastModified()
    val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

    return lastModified < oneDayAgo
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
  private fun showInRunToolWindow(
    processHandler: OSProcessHandler,
    consoleView: ConsoleView,
    title: String = "Restate Server"
  ) =
    WriteAction.computeAndWait<ConsoleView, Throwable> {
      // Create run content descriptor
      val contentDescriptor = RunContentDescriptor(
        null,
        processHandler,
        consoleView.component,
        title,
        RestateIcons.StartServer
      )

      // Show in Run tool window
      RunContentManager.getInstance(project).showRunContent(
        DefaultRunExecutor.getRunExecutorInstance(),
        contentDescriptor
      )

      consoleView
    }

  fun isStarting() = startRequested

  fun startServer() {
    ApplicationManager.getApplication().executeOnPooledThread {
      this.startServerInner()
    }
  }

  /**
   * Starts the Restate server.
   */
  fun startServerInner() {
    if (startRequested) {
      showNotification(
        project,
        "Restate is already running",
        "Restate is already running, only one instance can be run at the same time",
        NotificationType.INFORMATION
      )

      return
    }
    startRequested = true

    val consoleView = createConsoleView()

    try {
      LOG.info("Starting Restate server")

      // Print initial message
      consoleView.print("Starting Restate server...\n", ConsoleViewContentType.NORMAL_OUTPUT)

      // Get settings
      val settings = RestateSettings.getInstance()

      // Download the binary if it doesn't exist or if we want to check for updates
      if (settings.downloadRestateServer && shouldCheckForUpdates()) {
        consoleView.print("Downloading latest Restate binary...\n", ConsoleViewContentType.NORMAL_OUTPUT)
        downloadLatestRelease(project)
        consoleView.print("Download completed.\n", ConsoleViewContentType.NORMAL_OUTPUT)
      }

      val restateServerBinaryPath = if (settings.downloadRestateServer) {
        RESTATE_SERVER_DOWNLOAD_PATH.toString()
      } else {
        "restate-server"
      }

      // Create restate-data directory in project root
      val projectRootDir = project.guessProjectDir()
      val runCmd = if (projectRootDir != null) {
        val restateDataDir = Paths.get(projectRootDir.path, "restate-data")
        Files.createDirectories(restateDataDir)

        // Create base command line
        GeneralCommandLine(
          restateServerBinaryPath,
          "--base-dir", restateDataDir.toString(),
          "--node-name", "dev-cluster"
        )
      } else {
        // Couldn't compute the base dir!
        GeneralCommandLine(
          restateServerBinaryPath,
          "--node-name", "dev-cluster"
        )
      }

      // Add environment variables if provided
      val envVars = settings.getEnvironmentVariablesMap()
      if (envVars.isNotEmpty()) {
        runCmd.withEnvironment(envVars)
      }

      val processHandler =
        ProcessHandlerFactory.getInstance()
          .createColoredProcessHandler(runCmd)
      if (processHandler is KillableProcessHandler) {
        processHandler.setShouldKillProcessSoftly(true)
      }

      // Show in Run tool window
      showInRunToolWindow(processHandler, consoleView)

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

          startRequested = false
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
    } catch (e: Exception) {
      // Display error in the console
      consoleView.print(
        "\nERROR: Failed to start Restate server: ${e.message}\n",
        ConsoleViewContentType.ERROR_OUTPUT
      )
      startRequested = false

      LOG.warn("Error starting Restate server", e)
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