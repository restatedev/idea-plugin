package com.example.restate.toolwindow

import com.example.restate.servermanager.RestateServerTopic
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
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
  private var messageBusConnection: MessageBusConnection? = null

  companion object {
    private val LOG = Logger.getInstance(RestateToolWindow::class.java)
  }

  init {
    // Setup browser panel
    browserPanel.add(browser.component, BorderLayout.CENTER)
    browserPanel.preferredSize = Dimension(800, 600)

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

  /**
   * Creates a toolbar with actions for the Restate UI.
   */
  private fun createToolbar(): JComponent {
    val actionGroup = DefaultActionGroup().apply {
      add(object : AnAction("Reload", "Reload the Restate UI", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
          reloadUI()
        }
      })
    }

    val toolBar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLBAR,
      actionGroup,
      true
    )
    toolBar.targetComponent = browser.component

    return toolBar.component
  }

  fun getContent(): JComponent {
    val mainPanel = JPanel(BorderLayout())
    mainPanel.add(createToolbar(), BorderLayout.NORTH)
    mainPanel.add(browserPanel, BorderLayout.CENTER)
    return mainPanel
  }

  private fun openUI() {
    // Ensure UI operations are performed on the EDT
    ApplicationManager.getApplication().invokeLater {
      // Load the Restate UI in the embedded browser
      browser.loadURL("http://localhost:9070")
      toolWindow.component.revalidate()
      toolWindow.component.repaint()
    }
  }

  private fun closeUI() {
    ApplicationManager.getApplication().invokeLater {
      // TODO
    }
  }

  /**
   * Reloads the browser content.
   */
  private fun reloadUI() {
    ApplicationManager.getApplication().invokeLater {
      browser.loadURL("http://localhost:9070")
      browser.cefBrowser.reload()
      toolWindow.component.revalidate()
      toolWindow.component.repaint()
    }
  }
}
