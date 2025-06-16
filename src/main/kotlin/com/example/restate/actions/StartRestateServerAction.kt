package com.example.restate.actions

import com.example.restate.servermanager.RestateServerManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class StartRestateServerAction : AnAction() {
  companion object {
    private val LOG = Logger.getInstance(StartRestateServerAction::class.java)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Get the RestateServerManager instance for this project
    val serverManager = project.getUserData(RestateServerManager.RESTATE_SERVER_MANAGER_KEY)
    if (serverManager == null) {
      LOG.warn("RestateServerManager not found for project: ${project.name}")
      return
    }

    // Start the server
    serverManager.startServer()
  }

  override fun update(e: AnActionEvent) {
    // Enable the action only when a project is open
    e.presentation.isEnabled = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
