package com.example.restate.runconfiguration

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleRunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.util.concurrent.TimeUnit

class RestateRunConfigurationExtension : RunConfigurationExtension() {

  companion object {
    private val LOG = Logger.getInstance(RestateRunConfigurationExtension::class.java)
    private const val NOTIFICATION_GROUP_ID = "restate.notification"

    // Fully Qualified Names of Restate Service Annotations
    // IMPORTANT: Adjust these if the actual FQNs are different in the Restate Java SDK
    private val RESTATE_SERVICE_ANNOTATION_FQNS = listOf(
      "dev.restate.sdk.annotation.Service",
      "dev.restate.sdk.annotation.VirtualObject",
      "dev.restate.sdk.annotation.Workflow",
    )
  }

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?
  ) {
    // We can inject JVM arguments here if needed, but for now, we'll handle the logic in execute()
    // For example, if you wanted the app itself to trigger registration:
    // params.vmParametersList.addParametersString("-Drestate.register.auto=true")
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    // Only interested in ModuleRunConfiguration (e.g., Application, Spring Boot, Maven, Gradle)
    LOG.info("Is this applicable? " + (configuration as? ModuleRunConfiguration)?.name)
    return configuration is ModuleRunConfiguration || configuration is GradleRunConfiguration
  }

  override fun attachToProcess(
    configuration: RunConfigurationBase<*>,
    handler: ProcessHandler,
    runnerSettings: RunnerSettings?
  ) {
    LOG.info("Is this being called? " + (configuration as? ModuleRunConfiguration)?.name + "(for " + configuration?.type  +")")
    // This method is called when the process is about to start
    // We can use it to attach our process listener
    val moduleRunConfiguration = configuration as? ModuleRunConfiguration
    if (moduleRunConfiguration != null) {
      // Wrap the isRestateApplication check in a read action to ensure thread safety
      val isRestateApplication = ApplicationManager.getApplication().runReadAction<Boolean> {
        moduleRunConfiguration.modules.any { isRestateApplication(it) }
      }

      if (isRestateApplication) {
        LOG.info("That's a Restate application, awesome!")
        // Create a ProcessAdapter that will be added to the handler
        val processAdapter = object : ProcessAdapter() {
          override fun startNotified(event: ProcessEvent) {
            // This is called when the application process *starts*.
            // This is where we want to kick off our registration logic,
            // potentially with a delay or polling mechanism.
            LOG.info("Application process started. Scheduling Restate service registration...")
            // Schedule a task to run after a short delay, or to poll for readiness
            ApplicationManager.getApplication().executeOnPooledThread {
              try {
                // Give the application some time to start up (e.g., 5-10 seconds)
                TimeUnit.SECONDS.sleep(5) // Adjust this delay as needed

                // In a real scenario, you'd poll a health endpoint or listen to logs
                // to confirm the application is ready before calling registerService.
                // For this prototype, we just assume it's ready after the delay.
                registerRestateService(configuration.project, moduleRunConfiguration)
              } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                LOG.warn("Restate registration task interrupted.", e)
              } catch (e: Exception) {
                LOG.error("Failed to perform Restate service registration.", e)
                showNotification(
                  configuration.project,
                  "Restate Registration Failed",
                  "Error during registration: ${e.message}",
                  NotificationType.ERROR
                )
              }
            }
          }
        }
        // Add our process adapter to the handler
        handler.addProcessListener(processAdapter)
      } else {
        LOG.info("Not a restate application IDK bro")
      }
    }
  }

  // --- Recognition Logic ---
  private fun isRestateApplication(module: Module): Boolean {
    val project = module.project
    val moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)

    // All PSI operations need to be wrapped in a read action
      // Try to find the annotation classes themselves within the module's scope
      // This is a quick check if the SDK is present as a dependency
      for (annotationFQN in RESTATE_SERVICE_ANNOTATION_FQNS) {
        val psiClass = JavaPsiFacade.getInstance(project).findClass(annotationFQN, moduleScope)
        if (psiClass != null) {
          LOG.info("Found annotation FQN '$annotationFQN' in module dependencies. Likely a Restate app.")
          // Even if the annotation class exists, we should still look for actual usages
          // to be sure it's a service. Continue to the deeper scan.
        }
      }

      // Deeper scan: Iterate through all PSI classes in the module and check for annotations
      // This is more accurate but can be computationally more intensive for large projects,
      // as it requires indexes to be ready.
      val cache = PsiShortNamesCache.getInstance(project)
      val allClassNames = cache.getAllClassNames()

      for (className in allClassNames) {
        val psiClasses = cache.getClassesByName(className, moduleScope)
        for (psiClass in psiClasses) {
          if (psiClass.isAnnotationType || psiClass.isInterface || psiClass.isEnum || psiClass.isRecord) {
            continue // Skip annotations, interfaces, enums, records themselves, unless they are the service
          }
          for (annotationFQN in RESTATE_SERVICE_ANNOTATION_FQNS) {
            val annotation: PsiAnnotation? = psiClass.getAnnotation(annotationFQN)
            if (annotation != null) {
              LOG.info("Found class '${psiClass.qualifiedName}' with annotation '$annotationFQN'. This is a Restate service.")
              return true // Found a class annotated with a Restate service annotation
            }
          }
        }
      }

      return false
    }

  // --- Service Registration Logic (Stub) ---
  private fun registerRestateService(project: Project, runConfiguration: ModuleRunConfiguration) {
    LOG.info("STUB: Registering Restate service for module: ${runConfiguration.name}")

    // For the prototype, just show a notification and log.
    showNotification(
      project,
      "Restate Registration Success (Stub)",
      "Service registration logic would execute now.",
      NotificationType.INFORMATION
    )
  }

  private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
    ApplicationManager.getApplication().invokeLater {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(title, content, type)
        .notify(project)
    }
  }
}
