package com.example.restate.runconfiguration

import com.example.restate.RestateNotifications.showNotification
import com.intellij.execution.ExecutionListener
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
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf

class RestateExecutionListener(private val project: Project) : ExecutionListener {

  private val deploymentApiClient = DeploymentApi(ApiClient().setHost("127.0.0.1").setPort(9070))

  companion object {
    private val LOG = Logger.getInstance(RestateExecutionListener::class.java)
    private const val RESTATE_SERVER_STARTED_TEXT = "Restate HTTP Endpoint server started on port 9080"
  }

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val runProfile = env.runProfile
    LOG.info("Process started for runProfile: ${runProfile.name} (Executor: $executorId)")

    // Attach a ProcessAdapter to all process handlers to monitor their output
    handler.addProcessListener(object : ProcessAdapter() {
      private var isRestateApp = false

      override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
        // Only check stdout output
        if (outputType != ProcessOutputTypes.STDOUT) return

        val text = event.text

        // Check if the output contains the Restate Server started text
        if (!isRestateApp && text.contains(RESTATE_SERVER_STARTED_TEXT)) {
          isRestateApp = true
          LOG.info("Detected a Restate application: ${runProfile.name}. Output contains '$RESTATE_SERVER_STARTED_TEXT'")

          // Schedule service registration
          ApplicationManager.getApplication().executeOnPooledThread {
            try {
              registerRestateService()
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
      }
    })
  }

  // --- Service Registration Logic (Stub) ---
  private fun registerRestateService() {
    val registerDeploymentRequest = RegisterDeploymentRequestAnyOf()
    registerDeploymentRequest.uri = "http://localhost:9080"
    registerDeploymentRequest.force = true
    deploymentApiClient.createDeployment(RegisterDeploymentRequest(registerDeploymentRequest))
  }

}