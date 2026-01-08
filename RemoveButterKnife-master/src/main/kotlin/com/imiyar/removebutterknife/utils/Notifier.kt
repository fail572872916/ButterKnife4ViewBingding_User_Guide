package com.imiyar.removebutterknife.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifier {

    // 不再手动 new NotificationGroup，而是通过 Manager 获取
    // 注意：这里的 ID 应该与你想要显示的组名一致
    private const val GROUP_ID = "RemoveButterKnife Notification Group"

    fun notifyError(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
    }

    fun notifyInfo(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun notifyWarning(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, NotificationType.WARNING)
            .notify(project)
    }
}