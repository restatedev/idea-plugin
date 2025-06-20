package com.example.restate.toolwindow

import com.example.restate.RestateNotifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp

class RestateToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    if (JBCefApp.isSupported()) {
      val restateToolWindow = RestateToolWindow(toolWindow)
      val contentFactory = ContentFactory.getInstance()
      val content = contentFactory.createContent(restateToolWindow.getContent(project), "Restate UI", false)
      toolWindow.contentManager.addContent(content)
    } else {
      RestateNotifications.showNotification(
        project,
        "Cannot load Restate UI tool window",
        "The Restate UI cannot be loaded because this IDE doesn't support JCEF.",
        NotificationType.WARNING
      )
    }
  }
}
