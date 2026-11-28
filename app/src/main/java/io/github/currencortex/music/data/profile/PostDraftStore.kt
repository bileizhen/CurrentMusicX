package io.github.currencortex.music.data.profile

import android.content.Context
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.concurrent.ConcurrentHashMap

@Serializable
internal data class PostDraft(
    val title: String = "",
    val content: String = "",
    val attachments: List<PostAttachment> = emptyList(),
) {
    val isEmpty: Boolean get() = title.isEmpty() && content.isEmpty() && attachments.isEmpty()
    val canPublish: Boolean get() = title.validPostTitle() && content.postCharacterCount() in 1..POST_CONTENT_LIMIT
}

internal class PostDraftStore(context: Context, suffix: String, private val scope: CoroutineScope) {
    // Shared preferences are excluded by this app's file-only backup rules.
    private val preferences = context.getSharedPreferences("community-post-drafts$suffix", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val pending = ConcurrentHashMap<String, PendingDraft>()

    private class PendingDraft(val draft: PostDraft, val job: Job)

    suspend fun load(key: String): PostDraft = withContext(Dispatchers.IO) {
        pending[key]?.draft ?: read(key)
    }

    private fun read(key: String): PostDraft {
        val value = preferences.getString(key, null) ?: return PostDraft()
        return try {
            ApiJson.decodeFromString<PostDraft>(value)
        } catch (_: SerializationException) {
            PostDraft()
        }
    }

    fun flush(key: String) {
        pending[key]?.let { queueSave(key, it.draft, flush = it) }
    }

    fun save(key: String, draft: PostDraft) = queueSave(key, draft)

    private fun queueSave(key: String, draft: PostDraft, flush: PendingDraft? = null) {
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            if (flush == null && !draft.isEmpty) delay(300)
            writes.withLock {
                val current = pending[key] ?: return@withLock
                if (current.job != coroutineContext[Job]) return@withLock
                if (draft.isEmpty) preferences.edit().remove(key).apply()
                else preferences.edit().putString(key, ApiJson.encodeToString(PostDraft.serializer(), draft)).apply()
                pending.remove(key, current)
            }
        }
        val next = PendingDraft(draft, job)
        if (flush == null) {
            pending.put(key, next)?.job?.cancel()
        } else {
            if (!pending.replace(key, flush, next)) {
                job.cancel()
                return
            }
            flush.job.cancel()
        }
        job.start()
    }

    suspend fun clearPublished(key: String, draft: PostDraft) = withContext(Dispatchers.IO) {
        writes.withLock {
            val current = pending[key]
            if ((current?.draft ?: read(key)) != draft) return@withLock
            if (current != null && !pending.remove(key, current)) return@withLock
            current?.job?.cancel()
            preferences.edit().remove(key).apply()
        }
    }
}
