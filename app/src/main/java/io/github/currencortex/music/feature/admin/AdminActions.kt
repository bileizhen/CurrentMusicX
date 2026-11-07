package io.github.currencortex.music.feature.admin

import io.github.currencortex.music.data.admin.*

/** Presentation callbacks keep navigation and form tests independent of a real administrator session. */
internal interface AdminActions {
    fun select(tab: AdminTab)
    fun refresh()
    fun search(query: String, sort: String)
    fun page(delta: Int)
    fun clearOperationError()
    fun dismissMessage()
    fun userAction(target: AdminUser, action: AdminUserAction, value: String, level: Int?, done: () -> Unit)
    fun kick(session: AdminSession, done: () -> Unit)
    fun announcement(item: AdminAnnouncement?, title: String, body: String, link: String, pinned: Boolean, done: () -> Unit)
    fun announcementAction(item: AdminAnnouncement, action: String, done: () -> Unit)
    fun clearCache(done: () -> Unit)
}
