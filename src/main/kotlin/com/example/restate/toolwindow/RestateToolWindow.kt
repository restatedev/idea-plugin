package com.example.restate.toolwindow

import com.example.restate.servermanager.RestateServerTopic
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tool window for displaying the Restate UI.
 */
class RestateToolWindow(project: Project, private val toolWindow: ToolWindow) {
  // Browser component for displaying the Restate UI
  private val browser = JBCefBrowser()
  private val browserPanel = JPanel(BorderLayout())
  private var browserVisible = false
  private var messageBusConnection: MessageBusConnection? = null

  companion object {
    private val LOG = Logger.getInstance(RestateToolWindow::class.java)
  }

  init {
    // Setup browser panel
    browserPanel.add(browser.component, BorderLayout.CENTER)
    browserPanel.preferredSize = Dimension(800, 600)
    browserPanel.isVisible = false

    // Subscribe to server events using the message bus
    messageBusConnection = project.messageBus.connect()
    messageBusConnection?.subscribe(RestateServerTopic.TOPIC, object : RestateServerTopic {
      override fun onServerStarted() {
        openUI()
      }

      override fun onServerStopped() {
       closeUI()
      }
    })
  }

  fun getContent(): JComponent {
    val mainPanel = JPanel(BorderLayout())
    mainPanel.add(browserPanel, BorderLayout.CENTER)
    return mainPanel
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

  private fun closeUI() {
    ApplicationManager.getApplication().invokeLater {
    if (browserVisible) {
        browserPanel.isVisible = false
        browserVisible = false
        toolWindow.component.revalidate()
        toolWindow.component.repaint()
      }
    }
  }
}
