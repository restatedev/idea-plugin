package com.example.restate.runconfiguration

import com.example.restate.RestateNotifications.showNotification
import com.example.restate.servermanager.RestateServerManager
import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.restate.admin.api.DeploymentApi
import dev.restate.admin.client.ApiClient

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
          onRestateServiceDeploymentStarted(runProfile)
        }
      }
    })
  }

  private fun onRestateServiceDeploymentStarted(runProfile: RunProfile) =
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
        LOG.warn("Failed to perform Restate service registration for ${runProfile.name}.", e)
        showNotification(
          project,
          "Restate Registration Failed",
          "Tried to auto-register profile '${runProfile.name}' but failed: ${e.message}",
          NotificationType.WARNING
        )
      }
  }

}