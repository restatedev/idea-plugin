package com.example.restate.actions

import com.example.restate.toolwindow.RestateToolWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class StartRestateServerAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Get the RestateToolWindow instance for this project
        val restateToolWindow = project.getUserData(RestateToolWindow.RESTATE_TOOL_WINDOW) ?: return
        
        // Start the server if the tool window instance exists
        restateToolWindow.startServer()
    }
    
    override fun update(e: AnActionEvent) {
        // Enable the action only when a project is open
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}