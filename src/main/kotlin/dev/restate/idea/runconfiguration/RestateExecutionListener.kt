package dev.restate.idea.runconfiguration

import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.restate.idea.RestateNotifications.showNotification
import dev.restate.idea.RestateNotifications.showNotificationWithActions
import dev.restate.idea.servermanager.RestateServerManager
import dev.restate.idea.servermanager.RestateServerTopic

class RestateExecutionListener(private val project: Project) : ExecutionListener {

  companion object {
    private val LOG = Logger.getInstance(RestateExecutionListener::class.java)
    private const val SDK_JAVA_STARTED_TEXT = "Restate HTTP Endpoint server started on port 9080"
    private const val SDK_GO_STARTED_TEXT_1 = "Restate SDK started listening on [::]:9080"
    private const val SDK_GO_STARTED_TEXT_2 = "Restate SDK started listening on 127.0.0.1:9080"
  }

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val runProfile = env.runProfile
    LOG.info("Listening ${runProfile.name} (Executor: $executorId)")

    // Attach a ProcessAdapter to all process handlers to monitor their output
    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        // Check if the output contains the Restate Server started text
        if (event.text.contains(SDK_JAVA_STARTED_TEXT) || event.text.contains(SDK_GO_STARTED_TEXT_1) || event.text.contains(
            SDK_GO_STARTED_TEXT_2
          )
        ) {
          LOG.info("Detected a Restate service deployment: ${runProfile.name}")

          // Schedule service registration
          onRestateServiceDeploymentStarted(runProfile, true)
        }
      }

      override fun startNotified(event: ProcessEvent) {
        super.startNotified(event)
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
