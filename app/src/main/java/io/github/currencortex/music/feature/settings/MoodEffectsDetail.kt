package io.github.currencortex.music.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.currencortex.music.core.media.OriginalMoodState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.cos
import kotlin.math.sin

/** Native Mood detail, using the original vendor loops and the live model's emotion only. */
@Composable internal fun MoodEffectsDetail(
    state: OriginalMoodState,
    song: Song?,
    playing: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPlayback: () -> Unit,
) {
    var information by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = io.github.currencortex.music.ui.component.LocalMusicPageActive.current) { if (information) information = false else onBack() }
    val matched = state.enabled && song != null && state.songId == song.id
    val visual = state.visualExtra.takeIf { matched }
    val copy = visual?.get("copywriting") as? JsonObject
    val emotion = MoodEffectsPresentation.emotion(copy?.get("emotion")?.jsonPrimitive?.contentOrNull
        ?: state.emotion.takeIf { matched })
    val title = if (matched) copy?.get("keyword")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: emotion.keyword else "情绪调音师"
    val description = copy?.get("description")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: MoodEffectsPresentation.introduction
    val using = state.requestedEnabled || state.enabled
    val gold = Color(0xFFEBD4A2)
    val scene = MoodDetailScenes.forLabel(emotion.label.takeIf { matched } ?: "unUsed")
    val pageActive = LocalMusicPageActive.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("mood_detail")) {
        val pageHeight = maxHeight
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // The original emoji loops are 900 x 630. Size their viewport to that ratio instead
        // of letting the full-screen texture's zoom mode crop a horizontal scene to portrait.
        val heroWidth = minOf(maxWidth, pageHeight * .60f * (900f / 630f))
        val heroHeight = heroWidth * (630f / 900f)
        val heroTop = (pageHeight * .315f - heroHeight / 2).coerceAtLeast(0.dp)
        // Emotion changes cross-fade without replacing the current song or interrupting audio.
        Crossfade(scene.label, animationSpec = tween(550), label = "original mood scene") { label ->
            val scene = MoodDetailScenes.forLabel(label)
            val edge = Color(scene.heroBackground)
            Box(Modifier.fillMaxSize().background(edge)) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = heroTop).size(heroWidth, heroHeight)
                    .testTag("mood_emoji_hero")) {
                    EffectsVideo(scene.video, scene.poster, active = pageActive, Modifier.fillMaxSize())
                    // Blend the source's background edges into the same sampled color without
                    // an off-screen render layer, blur, or an extra video decoder.
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        0f to edge, .16f to Color.Transparent, .82f to Color.Transparent, 1f to edge)))
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                        0f to edge, .05f to Color.Transparent, .95f to Color.Transparent, 1f to edge)))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .25f),
                    .34f to Color.Black.copy(alpha = .18f),
                    .52f to Color.Black.copy(alpha = .68f),
                    .72f to Color.Black, 1f to Color.Black)))
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MoodDetailIcon("返回", onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("AI情绪调音师", color = Color.White.copy(alpha = .88f), fontSize = 14.sp)
                    Spacer(Modifier.width(5.dp)); EffectsMembership(4)
                }
                MoodDetailIcon("AI情绪调音师说明", { information = true }) {
                    Icon(Icons.Default.Info, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(19.dp))
                }
            }
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 28.dp)) {
                Spacer(Modifier.height((pageHeight * .55f - topInset - 52.dp).coerceAtLeast(90.dp)))
                val highlightedTitle = buildAnnotatedString {
                    val start = title.indexOf(scene.redWord).takeIf { scene.redWord.isNotBlank() && it >= 0 }
                    if (start == null) append(title) else {
                        append(title.substring(0, start))
                        withStyle(SpanStyle(color = Color(scene.color))) { append(scene.redWord) }
                        append(title.substring(start + scene.redWord.length))
                    }
                }
                Text(highlightedTitle, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("mood_emotion_title"))
                Spacer(Modifier.height(10.dp))
                Text(description, color = Color.White.copy(alpha = .68f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .10f))
                    .clickable(enabled = song != null, role = Role.Button, onClick = onPlayback)
                    .padding(10.dp).testTag("mood_song_playback"), verticalAlignment = Alignment.CenterVertically) {
                    if (song != null) EffectsArtwork(song.cover, Modifier.size(58.dp).clip(RoundedCornerShape(4.dp)), "当前歌曲封面")
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(song?.name ?: "播放歌曲，感受情绪调音", color = Color.White, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(if (matched && state.processing && playing) "情绪氛围增强中"
                            else if (state.loading) "正在匹配歌曲情绪"
                            else if (matched) "已匹配 · ${emotion.name}"
                            else "使用后 · 情绪氛围将增强", color = Color.White.copy(alpha = .48f), fontSize = 11.sp)
                    }
                    if (playing && song != null) Row(Modifier.size(30.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(3.dp, 14.dp).background(Color.White))
                        Spacer(Modifier.width(4.dp)); Box(Modifier.size(3.dp, 14.dp).background(Color.White))
                    } else Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(29.dp))
                Text("当前歌曲 · 调节说明", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                MoodActualAdjustments(visual, Color(scene.color))
                Text(if (matched && visual != null) copy?.get("description")?.jsonPrimitive?.contentOrNull
                    ?: MoodEffectsPresentation.fallbackDescription
                    else state.message, color = Color.White.copy(alpha = .58f), fontSize = 12.sp, lineHeight = 19.sp,
                    modifier = Modifier.testTag("mood_adjustment_status"))
                Spacer(Modifier.height(22.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp).height(46.dp)
                .clip(CircleShape).background(if (using) Color.White.copy(alpha = .09f) else gold)
                .border(if (using) 1.dp else 0.dp, if (using) Color.White.copy(alpha = .20f) else Color.Transparent, CircleShape)
                .clickable(role = Role.Button, onClick = onToggle).testTag("mood_toggle"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (using) "取消使用" else "立即使用", color = if (using) Color.White else Color(0xFF30251A),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (!using) { Spacer(Modifier.width(6.dp)); EffectsMembership(4) }
            }
        }
    }
    if (information) MoodInformationSheet { information = false }
}

@Composable private fun MoodDetailIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = label }
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

@Composable private fun MoodActualAdjustments(visual: JsonObject?, lineColor: Color) {
    val names = mapOf("stereo_width" to "立体声", "loudness" to "响度", "rhythm" to "节奏",
        "high_freq" to "高频", "space_size" to "混响", "vocal" to "人声", "richness" to "层次感")
    val dimensions = (visual?.get("dimensions") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .sortedBy { it["rank"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE }
    val plotted = dimensions.take(5).mapNotNull { row ->
        val values = (row["value"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.floatOrNull?.takeIf(Float::isFinite) }
            ?.takeIf { it.size == 2 } ?: return@mapNotNull null
        Triple(names[row["name"]?.jsonPrimitive?.content] ?: return@mapNotNull null, values[0], values[1])
    }
    if (plotted.size != 5) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(plotted) { progress.snapTo(0f); progress.animateTo(1f, tween(1500)) }
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(217, 255, 255, 255)
    } }
    // Exact original SVG geometry: 311 x 260, center (155.5,120), radius 83, five angles.
    Canvas(Modifier.fillMaxWidth().aspectRatio(311f / 260f).testTag("mood_adjustment_radar")) {
        val scale = size.width / 311f
        val center = Offset(155.5f * scale, 120f * scale)
        val radius = 83f * scale
        fun point(index: Int, magnitude: Float): Offset {
            val angle = (-90.0 + index * 72.0) * Math.PI / 180.0
            return center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius * magnitude
        }
        drawCircle(Color.White.copy(alpha = .02f), radius, center)
        for (index in 0..4) {
            val petal = Path().apply {
                moveTo(center.x, center.y); lineTo(point(index, 1f).x, point(index, 1f).y)
                lineTo(point((index + 1) % 5, 1f).x, point((index + 1) % 5, 1f).y); close()
            }
            drawPath(petal, Color.White.copy(alpha = .06f))
        }
        for (ring in 1..4) drawCircle(Color.White.copy(alpha = .16f), radius * ring / 4,
            center, style = Stroke(.5f * scale))
        for (after in listOf(false, true)) {
            val path = Path().apply {
                plotted.forEachIndexed { index, row ->
                    val p = point(index, (if (after) row.third else row.second).coerceIn(0f, 1f) * progress.value)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }; close()
            }
            val color = if (after) lineColor else Color.White.copy(alpha = .6f)
            drawPath(path, color.copy(alpha = if (after) .10f else .15f))
            drawPath(path, color, style = Stroke(1.5f * scale))
        }
        labelPaint.textSize = 13f * scale
        drawIntoCanvas { canvas -> plotted.forEachIndexed { index, row ->
            val p = point(index, 99f / 83f)
            labelPaint.textAlign = when (index) { 1, 2 -> android.graphics.Paint.Align.LEFT
                3, 4 -> android.graphics.Paint.Align.RIGHT; else -> android.graphics.Paint.Align.CENTER }
            canvas.nativeCanvas.drawText(row.first, p.x, p.y + labelPaint.textSize * .35f, labelPaint)
        } }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.Center) {
        listOf("调节前" to Color.White.copy(alpha = .6f), "调节后" to lineColor).forEach { (text, color) ->
            Box(Modifier.padding(start = 12.dp, top = 5.dp, end = 5.dp).size(6.dp).background(color, CircleShape))
            Text(text, color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        dimensions.take(2).forEach { row ->
            val name = row["name"]?.jsonPrimitive?.content
            val intensity = row["intensity"]?.jsonPrimitive?.content ?: "medium"
            if (name != null && name in names && intensity in setOf("weak", "medium", "strong")) {
                EffectsArtwork(NativeEffectsAssets.local("mood_detail_effect_${name}_${intensity}.png"),
                    Modifier.weight(1f).aspectRatio(151f / 115f).clip(RoundedCornerShape(10.dp)),
                    names[name] + when (intensity) { "weak" -> "微调中"; "strong" -> "突出中"; else -> "提升中" })
            }
        }
    }
}

@Composable private fun MoodInformationSheet(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * .61f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color.White)
                // Register this panel in the hit path so text/empty space cannot hit the backdrop.
                // No child gestures are consumed: text scrolling and the two close buttons stay usable.
                .pointerInput(Unit) { awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false); waitForUpOrCancellation()
                } }.testTag("mood_info_panel")
                .navigationBarsPadding().padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                    Text("AI情绪调音师说明", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.align(Alignment.CenterEnd)) { MoodDetailIcon("关闭说明", onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFF777777), modifier = Modifier.size(20.dp))
                    } }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    MoodEffectsPresentation.explanation.forEach { (heading, text) ->
                        Text(heading, color = Color(0xFF414141), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text, color = Color(0xFF909090), fontSize = 12.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(43.dp).clip(CircleShape)
                    .background(Color(0xFFFF393C)).clickable(role = Role.Button, onClick = onDismiss)
                    .testTag("mood_info_done"),
                    contentAlignment = Alignment.Center) { Text("知道了", color = Color.White, fontSize = 15.sp) }
            }
        }
    }
}
