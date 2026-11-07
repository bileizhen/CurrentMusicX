package io.github.currencortex.music.feature.listening

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.netease.listening.ListeningActionGate
import io.github.currencortex.music.data.netease.listening.ListeningArtistCapabilities
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val LocalListeningSocial = staticCompositionLocalOf<ListeningSocialActions?> { null }

/** Only the cloud report supplies this scope. Leaving it cancels pending reads and writes. */
@Composable internal fun ListeningSocialActionsProvider(container: AppContainer, enabled: Boolean,
    content: @Composable () -> Unit) {
    val account by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    key(container, enabled, account.revision) {
        val scope = rememberCoroutineScope()
        var actions by remember { mutableStateOf<ListeningSocialActions?>(null) }
        LaunchedEffect(container, enabled, account.revision) {
            if (enabled && account.loggedIn && !account.stale) {
                try {
                    val expected = container.neteaseListening.captureSession()
                    if (expected.revision != account.revision) return@LaunchedEffect
                    actions = ListeningSocialActions(container, expected, container.musicSession(), scope) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (_: Exception) { /* No action is exposed without a valid captured account. */ }
            }
        }
        LaunchedEffect(actions, lifecycle) {
            val current = actions ?: return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { current.refresh() }
        }
        CompositionLocalProvider(LocalListeningSocial provides actions, content = content)
    }
}

private class ListeningSocialActions(
    private val container: AppContainer, private val expected: NeteaseSession,
    private val requestSession: RequestSession, private val scope: CoroutineScope,
    private val error: (String) -> Unit,
) {
    private val repository = container.neteaseListening.social(expected)
    private val gate = ListeningActionGate(::check)
    private var likeVersion = 0L
    var likes by mutableStateOf<Set<Long>?>(null); private set
    val likeOverrides = mutableStateMapOf<Long, Boolean>()
    var capabilities by mutableStateOf<ListeningArtistCapabilities?>(null); private set
    val followed = mutableStateMapOf<Long, Boolean>()
    val busy = mutableStateMapOf<String, Boolean>()

    private fun check() {
        container.neteaseListening.checkSession(expected)
        if (container.musicSession() != requestSession) throw ApiException(ErrorKind.NeteaseSessionChanged)
    }

    suspend fun refresh() {
        try { check(); capabilities = repository.capabilities(); check() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { capabilities = null }
        val version = likeVersion
        try {
            check()
            val actual = container.neteaseSongActions.likedIds(fresh = true)
            check()
            if (version == likeVersion) { likes = actual; likeOverrides.clear() }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { /* Report collect values remain valid snapshots; unknown stays unknown. */ }
    }

    suspend fun loadArtist(id: Long) {
        val config = capabilities?.takeIf { it.read } ?: return
        if (busy["artist:$id"] == true || busy["artist-read:$id"] == true) return
        busy["artist-read:$id"] = true
        try { val value = repository.followed(id, config); check(); followed[id] = value }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { followed.remove(id); showError(failure) }
        finally { busy.remove("artist-read:$id") }
    }

    fun follow(id: Long) {
        val config = capabilities?.takeIf { it.read && it.follow } ?: return
        if (followed[id] != false || busy["artist:$id"] == true || busy["artist-read:$id"] == true) return
        busy["artist:$id"] = true
        scope.launch {
            try { if (repository.follow(id, config)) { check(); followed[id] = true } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { showError(failure) }
            finally { busy.remove("artist:$id") }
        }
    }

    fun retryArtist(id: Long) { scope.launch { loadArtist(id) } }

    fun like(song: ListeningMonthlyAction.Song, liked: Boolean?) {
        if (liked == null || busy["song:${song.id}"] == true) return
        busy["song:${song.id}"] = true
        scope.launch {
            try {
                gate.run("song:${song.id}") {
                    container.neteaseSongActions.setLiked(song.id, !liked, requestSession,
                        Song(song.id, song.name, song.artist, cover = song.cover),
                        expectedUid = expected.credential.profile?.uid)
                }.let { changed -> if (changed) {
                    likeVersion++
                    likeOverrides[song.id] = !liked
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { showError(failure) }
            finally { busy.remove("song:${song.id}") }
        }
    }

    private fun showError(failure: Exception) {
        error((failure as? ApiException)?.kind?.message ?: "操作失败，请稍后重试")
    }
}

@Composable internal fun ListeningArtistFollowButton(id: Long, onArtist: () -> Unit) {
    val actions = LocalListeningSocial.current ?: return
    val config = actions.capabilities?.takeIf { it.read } ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val s = LocalFootprintScale.current
    val followed = actions.followed[id]
    val busy = actions.busy["artist:$id"] == true || actions.busy["artist-read:$id"] == true
    LaunchedEffect(actions, config, id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { actions.loadArtist(id) }
    }
    // The native action config may disable writes while permitting state display.
    if (!config.follow && followed != true) return
    val color = FootprintRed.copy(alpha = if (followed == true) .4f else 1f)
    Box(Modifier.padding(start = (10 * s).dp, bottom = (5.333f * s).dp)
        .width((61.333f * s).dp).height((26 * s).dp).border((1 * s).dp, color, CircleShape)
        .clickable(enabled = !busy, role = Role.Button) {
            when (followed) { true -> onArtist(); false -> actions.follow(id); null -> actions.retryArtist(id) }
        }.testTag("listening_artist_follow_$id"), contentAlignment = Alignment.Center) {
        FootprintText(if (busy) "…" else when (followed) { true -> "已关注"; false -> "+ 关注"; null -> "重试" },
            size = 13f, weight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** Only month friend favourites and realtime week friend rows use this button; today is read-only. */
@Composable internal fun ListeningFriendLikeButton(song: ListeningMonthlyFriendSong) {
    val actions = LocalListeningSocial.current ?: return
    val action = song.action as? ListeningMonthlyAction.Song ?: return
    val s = LocalFootprintScale.current
    val liked = actions.likeOverrides[action.id] ?: actions.likes?.contains(action.id) ?: song.collected
    val busy = actions.busy["song:${action.id}"] == true
    if (liked == null) return
    AsyncImage(if (liked) ListeningCollectedHeart else ListeningEmptyHeart,
        if (liked) "取消红心" else "收藏歌曲",
        Modifier.size((24 * s).dp).padding((2 * s).dp)
            .clickable(enabled = !busy, role = Role.Button) { actions.like(action, liked) }
            .testTag("listening_friend_like_${action.id}"))
}

private const val ListeningCollectedHeart = "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/56013655066/aea0/a515/ea91/273762b7c43eb238f17f3fb75cfd01e3.png"
private const val ListeningEmptyHeart = "https://p5.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/56014257571/b35c/9a94/7bf5/06abeeb8d09928855f4ac896d2e08214.png"
