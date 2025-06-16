package com.example.restate.runconfiguration

import com.intellij.execution.ExecutionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.diagnostic.Logger

/**
 * Registers the RestateExecutionListener when a project is opened.
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
    }
}