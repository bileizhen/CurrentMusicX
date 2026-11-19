package io.github.currencortex.music.data.profile

import android.content.Context
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
internal data class PostDraft(
    val title: String = "",
    val content: String = "",
    val attachments: List<PostAttachment> = emptyList(),
) {
    val isEmpty: Boolean get() = title.isEmpty() && content.isEmpty() && attachments.isEmpty()
    val canPublish: Boolean get() = title.validPostTitle() && content.postCharacterCount() in 1..POST_CONTENT_LIMIT
}

internal class PostDraftStore(context: Context, suffix: String) {
    // Shared preferences are excluded by this app's file-only backup rules.
    private val preferences = context.getSharedPreferences("community-post-drafts$suffix", Context.MODE_PRIVATE)

    @Synchronized
    fun load(key: String): PostDraft {
        val value = preferences.getString(key, null) ?: return PostDraft()
        return try {
            ApiJson.decodeFromString<PostDraft>(value)
        } catch (_: SerializationException) {
            PostDraft()
        }
    }

    @Synchronized
    fun save(key: String, draft: PostDraft) {
        if (draft.isEmpty) preferences.edit().remove(key).apply()
        else preferences.edit().putString(key, ApiJson.encodeToString(PostDraft.serializer(), draft)).apply()
    }

    @Synchronized
    fun clearPublished(key: String, draft: PostDraft) {
        if (load(key) == draft) preferences.edit().remove(key).apply()
    }
}
