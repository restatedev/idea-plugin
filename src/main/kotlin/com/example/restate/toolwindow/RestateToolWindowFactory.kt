package com.example.restate.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class RestateToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val restateToolWindow = RestateToolWindow(project, toolWindow)
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(restateToolWindow.getContent(), "", false)
    toolWindow.contentManager.addContent(content)
  }
}
