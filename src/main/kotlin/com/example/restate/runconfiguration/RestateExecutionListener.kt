package com.example.restate.runconfiguration

import com.example.restate.RestateNotifications.showNotification
import com.example.restate.RestateNotifications.showNotificationWithActions
import com.example.restate.servermanager.RestateServerManager
import com.example.restate.servermanager.RestateServerTopic
import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class RestateExecutionListener(private val project: Project) : ExecutionListener {

  companion object {
    private val LOG = Logger.getInstance(RestateExecutionListener::class.java)
    private const val RESTATE_SERVER_STARTED_TEXT = "Restate HTTP Endpoint server started on port 9080"
  }

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val runProfile = env.runProfile
    LOG.info("Process started for runProfile: ${runProfile.name} (Executor: $executorId)")

    // Attach a ProcessAdapter to all process handlers to monitor their output
    handler.addProcessListener(object : ProcessAdapter() {
      override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
        // Only check stdout output
        if (outputType != ProcessOutputTypes.STDOUT) return

        val text = event.text

        // Check if the output contains the Restate Server started text
        if (text.contains(RESTATE_SERVER_STARTED_TEXT)) {
          LOG.info("Detected a Restate application: ${runProfile.name}. Output contains '$RESTATE_SERVER_STARTED_TEXT'")

          // Schedule service registration
          onRestateServiceDeploymentStarted(runProfile, true)
        }
      }
    })
  }

  private fun onRestateServiceDeploymentStarted(runProfile: RunProfile, tryToStartServer: Boolean) =
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        RestateServerManager.registerRestateService()
        LOG.info("Registration for Restate service ${runProfile.name} succeeded")
        showNotification(
          project,
          "Restate Service registration succeeded",
          "The service running on port 9080 have been auto-registered.",
          NotificationType.INFORMATION
        )
      } catch (e: Exception) {
        val restateServerManager = project.getUserData(RestateServerManager.RESTATE_SERVER_MANAGER_KEY)
        if (!tryToStartServer || restateServerManager == null || restateServerManager.isStarting()) {
          LOG.warn("Failed to perform Restate service registration for ${runProfile.name}.", e)
          showNotification(
            project,
            "Restate Registration Failed",
            "Tried to auto-register profile '${runProfile.name}' but failed. Please register manually. Reason: ${e.message}",
            NotificationType.WARNING
          )
          return@executeOnPooledThread
        }

        // Ask user if they want to start the restate server
        showNotificationWithActions(
          project,
          "Restate Server Required",
          "To interact with '${runProfile.name}' you need Restate server to be running. Would you like to start it now?",
          NotificationType.INFORMATION,
          object : AnAction("Start Server") {
            override fun actionPerformed(e: AnActionEvent) {
              tryToStartServerAndRegister(runProfile, restateServerManager)
            }
          },
          object : AnAction("Cancel") {
            override fun actionPerformed(e: AnActionEvent) {
              // Do nothing, just close the notification
            }
          }
        )
      }
    }

  private fun tryToStartServerAndRegister(runProfile: RunProfile, restateServerManager: RestateServerManager) {
    val connection = project.messageBus.connect();
    connection.subscribe(RestateServerTopic.TOPIC, object : RestateServerTopic {
      override fun onServerStarted() {
        onRestateServiceDeploymentStarted(runProfile, false)
        connection.disconnect()
      }

      override fun onServerStopped() {}
    })
    restateServerManager.startServer()
  }

}
