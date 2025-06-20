package com.example.restate

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

object RestateNotifications {
  private const val NOTIFICATION_GROUP_ID = "Restate Plugin Notifications"

  fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
    ApplicationManager.getApplication().invokeLater {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(title, content, type)
        .notify(project)
    }
  }

  fun showNotificationWithActions(
    project: Project,
    title: String,
    content: String,
    type: NotificationType,
    vararg actions: AnAction
  ) {
    ApplicationManager.getApplication().invokeLater {
      val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(title, content, type)

      // Add all actions to the notification
      actions.forEach { action ->
        notification.addAction(object : AnAction(action.templatePresentation.text) {
          override fun actionPerformed(e: AnActionEvent) {
            action.actionPerformed(e)
            notification.expire()
          }
        })
      }

      // Show the notification
      notification.notify(project)
    }
  }
}
