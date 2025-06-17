package com.example.restate.toolwindow

import com.example.restate.servermanager.RestateServerTopic
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Tool window for displaying the Restate UI.
 */
class RestateToolWindow(project: Project, private val toolWindow: ToolWindow) {
  // Constants for card layout
  private val BROWSER_PANEL = "BROWSER_PANEL"
  private val LABEL_PANEL = "LABEL_PANEL"

  // Browser component for displaying the Restate UI
  private val browser = JBCefBrowser()
  private val browserPanel = JPanel(BorderLayout())

  // Label component for displaying the "Start the Restate server first" message
  private val startServerLabel = JLabel("Start the Restate server first")
  private val labelPanel = JPanel(BorderLayout())

  // Content panel with card layout to switch between browser and label
  private val contentPanel = JPanel(CardLayout())

  private var messageBusConnection: MessageBusConnection? = null

  init {
    // Setup browser panel
    browserPanel.add(browser.component, BorderLayout.CENTER)

    // Setup label panel
    startServerLabel.horizontalAlignment = SwingConstants.CENTER
    labelPanel.add(startServerLabel, BorderLayout.CENTER)

    // Add panels to the content panel with card layout
    contentPanel.add(browserPanel, BROWSER_PANEL)
    contentPanel.add(labelPanel, LABEL_PANEL)

    // Initially show the label panel
    (contentPanel.layout as CardLayout).show(contentPanel, LABEL_PANEL)

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
          openUI()
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
    mainPanel.add(contentPanel, BorderLayout.CENTER)
    return mainPanel
  }

  private fun openUI() {
    ApplicationManager.getApplication().invokeLater {
      browser.loadURL("http://localhost:9070")
      browser.cefBrowser.reload()
      // Show the browser panel
      (contentPanel.layout as CardLayout).show(contentPanel, BROWSER_PANEL)
      toolWindow.component.revalidate()
      toolWindow.component.repaint()
    }
  }

  private fun closeUI() {
    ApplicationManager.getApplication().invokeLater {
      // Show the label panel
      (contentPanel.layout as CardLayout).show(contentPanel, LABEL_PANEL)
      toolWindow.component.revalidate()
      toolWindow.component.repaint()
    }
  }
}
