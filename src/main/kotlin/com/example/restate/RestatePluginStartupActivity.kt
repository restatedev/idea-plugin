package com.example.restate

import com.example.restate.runconfiguration.RestateExecutionListener
import com.example.restate.servermanager.RestateServerManager
import com.intellij.execution.ExecutionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Registers the RestateExecutionListener and RestateServerManager when a project is opened.
 */
class RestatePluginStartupActivity : ProjectActivity {

  companion object {
    private val LOG = Logger.getInstance(RestatePluginStartupActivity::class.java)
  }

  override suspend fun execute(project: Project) {
    LOG.info("RestatePluginStartupActivity initialized for project: ${project.name}")

    // Add our custom ExecutionListener to the project's message bus
    // This listener will be notified of all process starts/stops
    project.messageBus.connect().subscribe(
      ExecutionManager.EXECUTION_TOPIC,
      RestateExecutionListener(project)
    )
    LOG.info("RestateExecutionListener subscribed to ExecutionManager.EXECUTION_TOPIC.")

    // Create and register the RestateServerManager
    val serverManager = RestateServerManager(project)
    project.putUserData(RestateServerManager.RESTATE_SERVER_MANAGER_KEY, serverManager)
    LOG.info("RestateServerManager registered with project.")
  }
}