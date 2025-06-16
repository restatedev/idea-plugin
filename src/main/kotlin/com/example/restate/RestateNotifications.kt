package com.example.restate

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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

}