package com.example.restate.toolwindow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tool window for managing the Restate server.
 */
class RestateToolWindow(private val project: Project, private val toolWindow: ToolWindow) {
  private var serverRunning = false
  private var processHandler: ProcessHandler? = null
  private val LOG = Logger.getInstance(RestateToolWindow::class.java)
  private val startStopButton = JButton("Start Restate")
  private var consoleView: ConsoleView? = null
  private var runContentDescriptor: RunContentDescriptor? = null

  // Browser component for displaying the Restate UI
  private val browser = JBCefBrowser()
  private val browserPanel = JPanel(BorderLayout())
  private var browserVisible = false

  // Server manager for handling binary downloads and updates
  private val serverManager = RestateServerManager()

  companion object {
    val RESTATE_TOOL_WINDOW = Key.create<RestateToolWindow>("restateToolWindow")
  }

  init {
    startStopButton.addActionListener {
      if (serverRunning) {
        stopServer()
      } else {
        startServer()
      }
    }

    // Setup browser panel
    browserPanel.add(browser.component, BorderLayout.CENTER)
    browserPanel.preferredSize = Dimension(800, 600)
    browserPanel.isVisible = false

    project.putUserData(RESTATE_TOOL_WINDOW, this)
  }

  fun getContent(): JComponent {
    val mainPanel = JPanel(BorderLayout())

    // Create top panel for controls
    val topPanel = JPanel(BorderLayout())

    val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    buttonPanel.add(startStopButton)

    topPanel.add(buttonPanel, BorderLayout.WEST)

    // Add the top panel and browser panel to the main panel
    mainPanel.add(topPanel, BorderLayout.NORTH)
    mainPanel.add(browserPanel, BorderLayout.CENTER)

    return mainPanel
  }

  private fun createConsoleView(): ConsoleView {
    // Create a ConsoleViewImpl with ANSI color support enabled
    return (TextConsoleBuilderFactory.getInstance().createBuilder(project) as TextConsoleBuilderImpl)
      .also { it.isUsePredefinedMessageFilter = true }
      .console
  }

  private fun showInRunToolWindow(title: String) {
    val executor = DefaultRunExecutor.getRunExecutorInstance()

    // Create console view if it doesn't exist
    if (consoleView == null) {
      consoleView = createConsoleView()
    }

    // Create run content descriptor
    val contentDescriptor = RunContentDescriptor(
      null,
      processHandler,
      consoleView!!.component,
      title
    )

    // Show in Run tool window
    RunContentManager.getInstance(project).showRunContent(
      executor,
      contentDescriptor
    )

    // Store the descriptor
    runContentDescriptor = contentDescriptor
  }

  private fun checkForUpdates() {
    try {
      LOG.info("Checking for updates")

      // Create console view if it doesn't exist
      if (consoleView == null) {
        consoleView = createConsoleView()
      } else {
        consoleView?.clear()
      }

      // Show in Run tool window
      showInRunToolWindow("Restate Update Check")

      consoleView?.print("Checking for Restate server updates...\n", ConsoleViewContentType.NORMAL_OUTPUT)

      // Run the potentially blocking operation in a background thread
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          // Use the server manager to download the latest release
          serverManager.downloadLatestRelease()

          LOG.info("Successfully updated Restate binary")

          // Update UI on the EDT
          ApplicationManager.getApplication().invokeLater {
            consoleView?.print("Successfully updated Restate binary.\n", ConsoleViewContentType.NORMAL_OUTPUT)
          }
        } catch (e: Exception) {
          LOG.error("Failed to update Restate binary", e)

          // Update UI on the EDT
          ApplicationManager.getApplication().invokeLater {
            consoleView?.print("\nERROR: Failed to update Restate binary: ${e.message}\n", 
                              ConsoleViewContentType.ERROR_OUTPUT)
          }
        }
      }
    } catch (e: Exception) {
      LOG.error("Failed to update Restate binary", e)
      consoleView?.print("\nERROR: Failed to update Restate binary: ${e.message}\n", 
                        ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  fun startServer() {
    if (serverRunning) {
      return
    }
    try {
      LOG.info("Starting Restate server")

      // Create console view if it doesn't exist
      if (consoleView == null) {
        consoleView = createConsoleView()
      } else {
        consoleView?.clear()
      }

      // Print initial message
      consoleView?.print("Starting Restate server...\n", ConsoleViewContentType.NORMAL_OUTPUT)

      // Run potentially blocking operations in a background thread
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          // Download the binary if it doesn't exist or if we want to check for updates
          val binaryPath = serverManager.getBinaryPath()
          if (serverManager.shouldCheckForUpdates()) {
            ApplicationManager.getApplication().invokeLater {
              consoleView?.print("Downloading latest Restate binary...\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }

            serverManager.downloadLatestRelease()

            ApplicationManager.getApplication().invokeLater {
              consoleView?.print("Download completed.\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }
          }

          // Start the server process on the EDT to ensure thread safety with Swing components
          ApplicationManager.getApplication().invokeLater {
            try {
              // Start the server process
              val runCmd = GeneralCommandLine(
                binaryPath.toString(),
              )

              processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(runCmd)

              // Show in Run tool window
              showInRunToolWindow("Restate Server")

              // Attach process to console
              consoleView?.attachToProcess(processHandler!!)

              // Add a process listener to monitor the server
              processHandler?.addProcessListener(object : ProcessListener {
                override fun startNotified(event: ProcessEvent) {}

                override fun processTerminated(event: ProcessEvent) {
                  LOG.info("Restate server process terminated with exit code: ${event.exitCode}")
                  consoleView?.print("\nRestate server process terminated with exit code: ${event.exitCode}\n", 
                                    ConsoleViewContentType.SYSTEM_OUTPUT)

                  // Update UI on the EDT
                  ApplicationManager.getApplication().invokeLater {
                    serverRunning = false
                    startStopButton.text = "Start Restate"

                    // Hide the browser panel when the server is stopped
                    if (browserVisible) {
                      browserPanel.isVisible = false
                      browserVisible = false
                      toolWindow.component.revalidate()
                      toolWindow.component.repaint()
                    }
                  }
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                  // The console view will handle the text automatically since we attached the process

                  // Automatically open UI when server is ready
                  val text = event.text
                  if (text.contains("Server listening")) {
                    openUI()
                  }
                }
              })

              // Start the process
              processHandler?.startNotify()

              serverRunning = true
              startStopButton.text = "Stop Restate"
            } catch (e: Exception) {
              LOG.error("Error starting Restate server", e)
              serverRunning = false
              startStopButton.text = "Start Restate"

              // Log the error
              LOG.error("Failed to start Restate server: ${e.message}")

              // Display error in the console
              consoleView?.print("\nERROR: Failed to start Restate server: ${e.message}\n", 
                                ConsoleViewContentType.ERROR_OUTPUT)
            }
          }
        } catch (e: Exception) {
          LOG.error("Error in background thread while starting server", e)

          // Update UI on the EDT
          ApplicationManager.getApplication().invokeLater {
            serverRunning = false
            startStopButton.text = "Start Restate"

            // Display error in the console
            consoleView?.print("\nERROR: Failed to start Restate server: ${e.message}\n", 
                              ConsoleViewContentType.ERROR_OUTPUT)
          }
        }
      }
    } catch (e: Exception) {
      LOG.error("Error starting Restate server", e)
      serverRunning = false
      startStopButton.text = "Start Restate"

      // Log the error
      LOG.error("Failed to start Restate server: ${e.message}")

      // Display error in the console
      consoleView?.print("\nERROR: Failed to start Restate server: ${e.message}\n", 
                        ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  private fun stopServer() {
    try {
      LOG.info("Stopping Restate server")

      // Print message to console
      consoleView?.print("\nStopping Restate server...\n", ConsoleViewContentType.SYSTEM_OUTPUT)

      // Store a local reference to the process handler
      val currentProcessHandler = processHandler

      // Run the blocking operations in a background thread
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          currentProcessHandler?.destroyProcess()

          // Wait for the process to terminate
          if (currentProcessHandler != null && !currentProcessHandler.waitFor(5000)) {
            LOG.warn("Restate server process did not terminate gracefully, trying again")
            // Call destroyProcess again as a best effort
            currentProcessHandler.destroyProcess()

            // Wait a bit more
            if (!currentProcessHandler.waitFor(2000)) {
              LOG.warn("Process still not terminated after second attempt")
            }
          }

          // Update UI on the EDT
          ApplicationManager.getApplication().invokeLater {
            serverRunning = false
            startStopButton.text = "Start Restate"

            // Hide the browser panel when the server is stopped
            if (browserVisible) {
              browserPanel.isVisible = false
              browserVisible = false
              toolWindow.component.revalidate()
              toolWindow.component.repaint()
            }
          }
        } catch (e: Exception) {
          LOG.error("Error in background thread while stopping server", e)
        }
      }

    } catch (e: Exception) {
      LOG.error("Error stopping Restate server", e)
      consoleView?.print("\nERROR: Failed to stop Restate server: ${e.message}\n", 
                        ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  private fun openUI() {
    // Ensure UI operations are performed on the EDT
    ApplicationManager.getApplication().invokeLater {
      // Load the Restate UI in the embedded browser
      browser.loadURL("http://localhost:9070")

      // Make the browser panel visible if it's not already
      if (!browserVisible) {
        browserPanel.isVisible = true
        browserVisible = true

        // Refresh the UI to show the browser panel
        toolWindow.component.revalidate()
        toolWindow.component.repaint()
      }
    }
  }
}
