package io.github.currencortex.music.feature.musicservice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.musicservice.ConnectedMusicService
import io.github.currencortex.music.data.musicservice.MusicServiceConnections
import io.github.currencortex.music.data.musicservice.MusicServicePage
import io.github.currencortex.music.data.musicservice.MusicServiceResource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicChoiceDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.TextField

private val searchKinds = linkedMapOf("song" to "歌曲", "album" to "专辑", "artist" to "歌手", "playlist" to "歌单")

private data class ServiceQuery(val operation: String, val input: JsonObject = buildJsonObject {}, val offset: Int = 0)
private data class PendingWrite(val operation: String, val label: String, val input: JsonObject)

@Composable
internal fun MusicServicesScreen(repo: MusicServiceConnections, onPlay: (List<Song>, Int) -> Unit,
    onVideo: (Song) -> Unit, onDownload: (Song) -> Unit) {
    SettingsUiScope {
        val services by repo.services.collectAsStateWithLifecycle()
        val pending by repo.pendingConnection.collectAsStateWithLifecycle()
        val authorized by repo.lastAuthorizedService.collectAsStateWithLifecycle()
        val browser = LocalUriHandler.current
        var selected by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(authorized) {
            authorized?.let { selected = it; repo.lastAuthorizedService.value = null }
        }
        val service = services.firstOrNull { it.key == selected } ?: services.firstOrNull()
        var platform by rememberSaveable(service?.key) { mutableStateOf("") }
        val activePlatform = platform.takeIf { service?.descriptor?.platforms?.containsKey(it) == true }
            ?: service?.descriptor?.platforms?.keys?.firstOrNull().orEmpty()
        val context = Triple(service?.key, activePlatform, service?.account)
        val scope = key(context) { rememberCoroutineScope() }
        var keyword by rememberSaveable(context) { mutableStateOf("") }
        var kind by rememberSaveable { mutableStateOf("song") }
        var busy by remember(context) { mutableStateOf(false) }
        var message by remember(context) { mutableStateOf("") }
        var page by remember(context) { mutableStateOf(MusicServicePage()) }
        var labels by remember(context) { mutableStateOf(emptyList<String>()) }
        var last by remember(context) { mutableStateOf<ServiceQuery?>(null) }
        var focused by remember(context) { mutableStateOf<MusicServiceResource?>(null) }
        var write by remember(context) { mutableStateOf<PendingWrite?>(null) }
        var editText by remember(context) { mutableStateOf("") }
        var remove by remember(context) { mutableStateOf<ConnectedMusicService?>(null) }
        var pendingTrack by remember(context) { mutableStateOf<MusicServiceResource?>(null) }

        fun run(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            message = ""
            scope.launch {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) {
                    message = if (e is io.github.currencortex.music.core.network.ApiException) e.message.orEmpty()
                    else if (e is IllegalArgumentException) e.message?.take(160) ?: "参数无效"
                    else "音乐服务请求失败，请重试"
                } finally { busy = false }
            }
        }

        suspend fun authorizeIfNeeded(connection: ConnectedMusicService, operation: String): Boolean {
            val required = connection.descriptor.capability(activePlatform, operation)?.scope ?: return true
            if (required in connection.authorizedScopes) return true
            browser.openUri(repo.beginAuthorization(connection.key, setOf(required)))
            message = "授权完成后可继续使用此功能"
            return false
        }

        fun query(query: ServiceQuery, append: Boolean = false) = run {
            val connection = service ?: return@run
            if (!authorizeIfNeeded(connection, query.operation)) return@run
            val response = repo.call(connection.key, activePlatform, query.operation, buildJsonObject {
                query.input.forEach { (key, value) -> put(key, value) }
                put("offset", query.offset)
            }, expectedConnection = connection)
            val rows = response["items"] as? JsonArray
            val comments = response["comments"] as? JsonArray
            if (query.operation in setOf("discover.hot", "discover.suggest")) {
                labels = rows.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                page = MusicServicePage()
            } else if (query.operation == "track.comments") {
                labels = comments.orEmpty().mapNotNull { ((it as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull }
                page = MusicServicePage()
            } else if (query.operation == "playback.qualities") {
                labels = (response["levels"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                page = MusicServicePage()
            } else {
                labels = emptyList()
                val next = ApiJson.decodeFromJsonElement<MusicServicePage>(response)
                page = if (append) next.copy(items = page.items + next.items, tracks = page.tracks + next.tracks) else next
                next.resource?.let { focused = it }
            }
            last = query
            if (labels.isEmpty() && page.items.isEmpty() && page.tracks.isEmpty() && page.albums.isEmpty() && page.resource == null)
                message = "暂无结果"
        }

        fun doSearch(text: String = keyword) = query(ServiceQuery("catalog.search", buildJsonObject {
            put("query", text); put("kind", kind)
        }))

        fun supported(op: String) = service?.descriptor?.capability(activePlatform, op) != null
        fun id(resource: MusicServiceResource) = buildJsonObject { put("id", resource.id) }

        fun play(resource: MusicServiceResource) = run {
            val connection = service ?: return@run
            if (!authorizeIfNeeded(connection, "playback.resolve")) return@run
            val items = (page.tracks + page.items).filter { it.kind == "song" }.distinctBy { it.id }
                .ifEmpty { listOf(resource) }.let { if (resource in it) it else listOf(resource) }
            onPlay(items.map { it.song(connection.key, activePlatform) }, items.indexOf(resource).coerceAtLeast(0))
        }

        SettingsPage("外接音乐服务", "external_music_services") {
            item("connections") {
                SettingsSection("已连接的服务", MusicIcons.Storage) {
                    if (services.isEmpty()) SettingsHint("在服务网页点「在流声音乐中连接」")
                    else {
                        SettingsOptionsRow("服务", service?.descriptor?.name.orEmpty(), services.map {
                            SettingsOption(it.key, it.descriptor.name, it.key == service?.key,
                                summary = runCatching { io.github.currencortex.music.data.musicservice.httpsUrl(it.descriptor.apiBase).host }.getOrNull(),
                                onSelect = { selected = it.key })
                        }, Modifier.testTag("choose_music_service"), enabled = !busy)
                        service?.let { connection ->
                            val platforms = connection.descriptor.platforms.keys
                            SettingsOptionsRow("曲库", activePlatform, platforms.map {
                                SettingsOption(it, it, it == activePlatform, onSelect = { platform = it })
                            }, Modifier.testTag("choose_music_service_platform"), enabled = !busy)
                            SettingsPreference("断开连接", onClick = { remove = connection }, enabled = !busy)
                            val lyricsScope = connection.descriptor.capability(activePlatform, "track.lyrics")?.scope
                            if (lyricsScope != null && lyricsScope !in connection.authorizedScopes) {
                                SettingsPreference("授权读取歌词", enabled = !busy, onClick = { run {
                                    authorizeIfNeeded(connection, "track.lyrics")
                                } })
                            }
                            if (connection.descriptor.authorization != null && connection.authorizedScopes.isNotEmpty()) {
                                SettingsPreference("重新授权", enabled = !busy, onClick = { run {
                                    browser.openUri(repo.beginAuthorization(connection.key,
                                        connection.descriptor.platforms.values.flatten().mapNotNull { it.scope }
                                            .toSet().intersect(connection.authorizedScopes)))
                                } })
                            }
                        }
                    }
                    if (busy) SettingsHint("正在处理…")
                    if (message.isNotBlank()) SettingsHint(message)
                }
            }

            if (supported("catalog.search")) item("search") {
                SettingsSection("搜索", MusicIcons.List) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(keyword, { keyword = it.take(60) }, label = "搜索音乐", singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("music_service_query"))
                        SettingsOptionsRow("类型", searchKinds.getValue(kind), searchKinds.map { (key, label) ->
                            SettingsOption(key, label, kind == key, onSelect = { kind = key })
                        }, Modifier.testTag("music_service_query_kind"), enabled = !busy)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MusicTextAction("搜索", { doSearch() }, enabled = keyword.isNotBlank() && !busy)
                            if (supported("discover.suggest")) MusicTextAction("联想", {
                                query(ServiceQuery("discover.suggest", buildJsonObject { put("query", keyword) }))
                            }, enabled = keyword.isNotBlank() && !busy)
                        }
                    }
                }
            }

            val browse = listOf("discover.hot" to "热搜", "discover.charts" to "榜单", "discover.recommendations" to "每日推荐",
                "roaming.songs" to "私人漫游", "library.favorites" to "收藏", "library.playlists" to "个人歌单",
                "history.recent" to "最近播放", "library.cloud" to "云盘曲库").filter { supported(it.first) }
            if (browse.isNotEmpty() || supported("library.playlist.create") || supported("history.record")) item("browse") {
                SettingsSection("浏览与曲库", MusicIcons.List) {
                    for ((op, label) in browse) SettingsPreference(label, enabled = !busy, onClick = { query(ServiceQuery(op)) })
                    if (supported("library.playlist.create")) SettingsPreference("新建歌单", enabled = !busy, onClick = {
                        editText = ""; write = PendingWrite("library.playlist.create", "新建歌单", buildJsonObject {})
                    })
                    if (supported("history.record")) SettingsPreference("授权记录播放进度", enabled = !busy, onClick = { run {
                        val connection = service ?: return@run
                        if (authorizeIfNeeded(connection, "history.record")) message = "已授权记录此服务的播放进度"
                    } })
                    if (pendingTrack != null) SettingsHint("打开个人歌单，选择「加入待添加歌曲」")
                }
            }

            focused?.let { resource -> item("focused") {
                SettingsSection(resource.name, MusicIcons.List) {
                    if (resource.kind == "song") {
                        if (supported("playback.resolve")) SettingsPreference("播放", enabled = !busy, onClick = { play(resource) })
                        if (supported("playback.qualities")) SettingsPreference("音质档位", enabled = !busy, onClick = {
                            query(ServiceQuery("playback.qualities", id(resource)))
                        })
                        if (supported("track.comments")) SettingsPreference("查看评论", enabled = !busy, onClick = {
                            query(ServiceQuery("track.comments", id(resource)))
                        })
                        if (supported("track.comment.add")) SettingsPreference("发表评论", enabled = !busy, onClick = {
                            editText = ""; write = PendingWrite("track.comment.add", "发表评论", id(resource))
                        })
                        if (supported("library.favorite.set")) for (value in listOf(true, false)) {
                            val label = if (value) "收藏歌曲" else "取消收藏"
                            SettingsPreference(label, enabled = !busy, onClick = {
                                write = PendingWrite("library.favorite.set", label, buildJsonObject {
                                    put("id", resource.id); put("selected", value)
                                })
                            })
                        }
                        if (supported("library.playlist.tracks")) SettingsPreference("添加到个人歌单", enabled = !busy, onClick = {
                            pendingTrack = resource; query(ServiceQuery("library.playlists"))
                        })
                        if (supported("playback.download")) SettingsPreference("下载", enabled = !busy, onClick = { run {
                            val connection = service ?: return@run
                            if (authorizeIfNeeded(connection, "playback.download")) onDownload(resource.song(connection.key, activePlatform))
                        } })
                        if (supported("playback.mv") && resource.videoId.isNotBlank()) SettingsPreference("观看 MV", enabled = !busy, onClick = { run {
                            val connection = service ?: return@run
                            if (authorizeIfNeeded(connection, "playback.mv")) onVideo(resource.copy(id = resource.videoId).song(connection.key, activePlatform))
                        } })
                    }
                    if (resource.kind == "playlist" && last?.operation == "library.playlist") {
                        val trackToAdd = pendingTrack
                        if (trackToAdd != null && supported("library.playlist.tracks")) SettingsPreference("加入待添加歌曲", trackToAdd.name,
                            enabled = !busy, onClick = { write = PendingWrite("library.playlist.tracks", "加入歌单", buildJsonObject {
                                put("id", resource.id); put("selected", true); putJsonArray("trackIds") { add(JsonPrimitive(trackToAdd.id)) }
                            }) })
                        if (supported("library.playlist.edit")) SettingsPreference("编辑名称", enabled = !busy, onClick = {
                            editText = resource.name; write = PendingWrite("library.playlist.edit", "编辑歌单", id(resource))
                        })
                        if (supported("library.playlist.delete")) SettingsPreference("删除歌单", enabled = !busy, onClick = {
                            write = PendingWrite("library.playlist.delete", "删除歌单", id(resource))
                        })
                    }
                }
            } }

            if (page.items.isNotEmpty() || page.tracks.isNotEmpty() || page.albums.isNotEmpty() || labels.isNotEmpty()) item("results") {
                SettingsSection("结果", MusicIcons.List) {
                    for (label in labels) SettingsPreference(label,
                        onClick = if (last?.operation in setOf("discover.hot", "discover.suggest")) ({
                            keyword = label.take(60); doSearch(keyword)
                        }) else null)
                    val parent = last?.operation
                    for (resource in page.items + page.tracks + page.albums) {
                        SettingsPreference(resource.name, resource.artists.ifBlank { searchKinds[resource.kind].orEmpty() },
                            enabled = !busy, onClick = {
                                focused = resource
                                val op = when {
                                    parent == "library.playlists" -> "library.playlist"
                                    resource.kind == "song" -> "catalog.track"
                                    else -> "catalog.${resource.kind}"
                                }
                                if (supported(op) && parent != "library.playlist") query(ServiceQuery(op, id(resource)))
                            }, trailing = when {
                                resource.kind != "song" -> null
                                parent == "library.playlist" && supported("library.playlist.tracks") && page.resource != null -> ({
                                    MusicTextAction("移出", {
                                        write = PendingWrite("library.playlist.tracks", "移出歌单", buildJsonObject {
                                            put("id", requireNotNull(page.resource).id); put("selected", false)
                                            putJsonArray("trackIds") { add(JsonPrimitive(resource.id)) }
                                        })
                                    }, enabled = !busy)
                                })
                                supported("playback.resolve") -> ({ MusicTextAction("播放", { play(resource) }, enabled = !busy) })
                                else -> null
                            })
                    }
                    val original = last
                    val more = page.nextOffset
                    if (original != null && more != null && more > original.offset) SettingsPreference("加载更多", enabled = !busy,
                        onClick = { query(original.copy(offset = more), append = true) })
                }
            }
        }

        pending?.let { (url, descriptor) ->
            val dismiss = { repo.pendingConnection.value = null }
            ServiceConfirmation("连接 ${descriptor.name}", dismiss, "连接", enabled = !busy, confirm = {
                run { selected = repo.connect(url, descriptor) }
            }) {
                SettingsHint("服务域名 · ${io.github.currencortex.music.data.musicservice.httpsUrl(descriptor.apiBase).host}")
                SettingsHint(descriptor.platforms.values.flatten().map { it.label }.distinct().joinToString("、"))
            }
        }
        remove?.let { connection ->
            ServiceConfirmation("断开 ${connection.descriptor.name}", { remove = null }, "断开", confirm = {
                remove = null
                run {
                    val revoked = repo.disconnect(connection.key)
                    message = if (revoked) "已断开连接" else "已断开，可在服务网页检查授权是否撤销"
                }
            }) { SettingsHint("本机的服务授权和连接记录将被清除") }
        }
        write?.let { action ->
            val textKey = when (action.operation) {
                "track.comment.add" -> "text"
                "library.playlist.create", "library.playlist.edit" -> "name"
                else -> null
            }
            ServiceConfirmation(action.label, { write = null }, "确认", enabled = textKey == null || editText.isNotBlank(), confirm = {
                run {
                    val connection = service ?: return@run
                    if (!authorizeIfNeeded(connection, action.operation)) return@run
                    val result = repo.call(connection.key, activePlatform, action.operation, buildJsonObject {
                        action.input.forEach { (key, value) -> put(key, value) }
                        if (textKey != null) put(textKey, editText)
                    }, confirm = true, expectedConnection = connection)
                    write = null
                    if (action.operation == "library.playlist.tracks") {
                        pendingTrack = null
                        if (action.input["selected"]?.jsonPrimitive?.booleanOrNull == false) {
                            val ids = (action.input["trackIds"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()
                            page = page.copy(tracks = page.tracks.filterNot { it.id in ids })
                        }
                    }
                    if (action.operation == "library.playlist.delete") focused = null
                    (result["resource"] as? JsonObject)?.let { focused = ApiJson.decodeFromJsonElement(it) }
                    message = "已保存"
                }
            }) {
                if (textKey != null) TextField(editText, { editText = it.take(if (textKey == "text") 300 else 100) },
                    label = if (textKey == "text") "评论" else "歌单名称", modifier = Modifier.fillMaxWidth())
                else SettingsHint("将向当前连接的音乐服务提交此操作")
            }
        }
    }
}

@Composable
private fun ServiceConfirmation(title: String, dismiss: () -> Unit, label: String, enabled: Boolean = true,
    confirm: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    MusicChoiceDialog(title, dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicDialogButton("取消", dismiss, Modifier.weight(1f))
            MusicDialogButton(label, confirm, Modifier.weight(1f), enabled = enabled)
        }
    }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
