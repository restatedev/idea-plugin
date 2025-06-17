package com.example.restate.toolwindow

import com.example.restate.servermanager.RestateServerTopic
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tool window for displaying the Restate UI.
 */
class RestateToolWindow(private val toolWindow: ToolWindow) {
  // Browser component for displaying the Restate UI
  private val browser = JBCefBrowser()
  private val browserPanel = JPanel(BorderLayout())

  init {
    // Setup browser panel
    browserPanel.add(browser.component, BorderLayout.CENTER)
    browserPanel.preferredSize = Dimension(800, 600)
    browserPanel.isVisible = true
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

  fun getContent(project: Project): JComponent {
    val mainPanel = JPanel(BorderLayout())
    mainPanel.add(createToolbar(), BorderLayout.NORTH)
    mainPanel.add(browserPanel, BorderLayout.CENTER)

    // Initialize browser with a default message
    loadDefaultPage()

    // Subscribe to server events using the message bus
    project.messageBus.connect().subscribe(RestateServerTopic.TOPIC, object : RestateServerTopic {
      override fun onServerStarted() {
        openUI()
      }

      override fun onServerStopped() {
        closeUI()
      }
    })

    return mainPanel
  }

  private fun openUI() {
    ApplicationManager.getApplication().invokeLater {
      browser.loadURL("http://localhost:9070")
      browser.cefBrowser.reload()
      toolWindow.component.revalidate()
      toolWindow.component.repaint()
    }
  }

  private fun closeUI() {
    ApplicationManager.getApplication().invokeLater {
      loadDefaultPage()
    }
  }

  /**
   * Loads a default page with a message indicating that the Restate server is not running.
   */
  private fun loadDefaultPage() {
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
          <style>
            body {
              font-family: Arial, sans-serif;
              margin: 0;
              padding: 20px;
              background-color: #f5f5f5;
              color: #333;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
              height: 100vh;
              text-align: center;
            }
            .container {
              background-color: white;
              border-radius: 8px;
              padding: 30px;
              box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
              max-width: 600px;
            }
            h1 {
              color: #4285f4;
              margin-bottom: 20px;
            }
            p {
              line-height: 1.6;
              margin-bottom: 15px;
            }
            .action {
              margin-top: 20px;
              font-weight: bold;
            }
          </style>
        </head>
        <body>
          <div class="container">
            <h1>Restate UI</h1>
            <p>The Restate server is not currently running.</p>
            <p>To view the Restate UI, you need to start the Restate server first.</p>
            <p class="action">Use the "Start Restate Server" action from the Tools menu to start the server.</p>
          </div>
        </body>
        </html>
      """.trimIndent()

    browser.loadHTML(htmlContent)
    browser.cefBrowser.reload()
    toolWindow.component.revalidate()
    toolWindow.component.repaint()
  }
}
