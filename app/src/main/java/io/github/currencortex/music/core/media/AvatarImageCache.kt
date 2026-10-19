package io.github.currencortex.music.core.media

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.currencortex.music.data.profile.AvatarCache
import io.github.currencortex.music.data.profile.versionedAvatarUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Evict only the replaced avatars; keep covers, decorations and other accounts' images. */
class AvatarImageCache(
    context: Context,
    namespace: String = "",
    private val imageLoader: () -> ImageLoader = { SingletonImageLoader.get(context.applicationContext) },
) : AvatarCache {
    private val preferences = context.getSharedPreferences("avatar-cache-versions$namespace", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    override val versions: Map<String, String>
        get() = preferences.all.mapNotNull { (url, value) -> (value as? String)?.let { url to it } }.toMap()

    override suspend fun invalidate(urls: Set<String>): Map<String, String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val previous = versions
            val keys = urls.flatMap { listOf(it, versionedAvatarUrl(it, previous[it])) }.toSet()
            val loader = imageLoader()
            loader.memoryCache?.let { memory ->
                // Include every decoded size/transformation of this asset.
                memory.keys.filter { it.key in keys }.forEach(memory::remove)
            }
            keys.forEach { loader.diskCache?.remove(it) }
            val version = UUID.randomUUID().toString()
            val updated = previous + urls.associateWith { version }
            // Persist before notifying UI: old in-flight requests cannot make a later
            // launch reuse the obsolete cache key, even if they finish after eviction.
            check(preferences.edit().apply { urls.forEach { putString(it, version) } }.commit())
            updated
        }
    }
}
