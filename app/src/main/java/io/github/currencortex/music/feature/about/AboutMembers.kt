// Ported from LeiFetch AboutScreen, GPL-3.0; same avatar rows, member dialogs and AnimatedList motion.
package io.github.currencortex.music.feature.about

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import io.github.currencortex.music.core.config.AboutMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

internal data class MemberFocus(val member: AboutMember, val group: String)

/** QQ 头像内存缓存；关于页每次打开最多加载几张头像，无需落盘。
 *  不同网络下各端点可用性不一（实测 headimg_dl 在部分网络 400），按序回退。
 *  缓存键带规格：列表用 100，详情弹窗用 640。 */
private object QqAvatarCache {
    private val cache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }
    private val endpoints = listOf(
        "https://q1.qlogo.cn/g?b=qq&nk=%s&s=%d",
        "https://thirdqq.qlogo.cn/g?b=qq&nk=%s&s=%d",
        "https://q1.qlogo.cn/headimg_dl?dstuin=%s&spec=%d",
    )
    suspend fun load(qq: String, spec: Int = 100): Bitmap? {
        val key = "$qq@$spec"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            endpoints.firstNotNullOfOrNull { pattern ->
                try {
                    val connection = URL(pattern.format(qq, spec)).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent",
                        "CurrentMusic avatar/1.0")
                    try {
                        BitmapFactory.decodeStream(connection.inputStream)?.also { cache.put(key, it) }
                    } finally {
                        connection.disconnect()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        }
    }
}

@Composable
private fun QqAvatar(qq: String, size: Dp = 44.dp, spec: Int = 100, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, qq, spec) { value = QqAvatarCache.load(qq, spec) }
    Box(modifier.size(size).clip(CircleShape)
        .background(colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) Image(loaded.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(qq.takeLast(2), fontSize = (size.value * 0.27f).sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun MemberRow(member: AboutMember, onClick: () -> Unit) {
    BasicComponent(
        title = member.name,
        summary = member.role,
        startAction = { QqAvatar(member.qq, modifier = Modifier.padding(end = 6.dp)) },
        endActions = {
            // 与下方链接行同一箭头，提示这一行可以点开。
            Image(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                modifier = Modifier.size(width = 10.dp, height = 16.dp).align(Alignment.CenterVertically),
            )
        },
        onClick = onClick,
    )
}

/** ReactBits AnimatedList 的条目动效：条目自身超过一半进入屏幕后，从 scale 0.7 / 全透明
 *  淡入到 1；离开视口复位，再滚回来会重播（对应其 useInView 的 amount 0.5 与 once: false）。
 *  每条延时都是固定的 100ms，靠条目先后越过一半视口自然错开，而不是按序号累加延时。
 *
 *  可见性不能用 onGloballyPositioned 判断：滚动时 LazyColumn 只重新摆放已测量的条目、
 *  不会重新布局，位置回调不再触发，屏幕外的条目会永远停在透明态。所以位置回调只用来记下
 *  条目在宿主列表项内的偏移与自身高度（滚动中都是定值），可见比例改由滚动信息实时计算。 */
@Composable
internal fun AnimatedListItem(
    listState: LazyListState,
    hostKey: String,
    content: @Composable () -> Unit,
) {
    var offsetInHost by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    val inView by remember(listState, hostKey) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val host = layout.visibleItemsInfo.firstOrNull { it.key == hostKey }
            if (host == null || heightPx <= 0f) {
                false
            } else {
                val top = host.offset + offsetInHost
                val visible = (top + heightPx).coerceAtMost(layout.viewportEndOffset.toFloat()) -
                        top.coerceAtLeast(layout.viewportStartOffset.toFloat())
                visible >= heightPx * 0.5f
            }
        }
    }

    Box(
        Modifier.onGloballyPositioned { coordinates ->
            offsetInHost = coordinates.positionInParent().y
            heightPx = coordinates.size.height.toFloat()
        }
    ) {
        val progress by animateFloatAsState(
            targetValue = if (inView) 1f else 0f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 100,
                easing = FastOutSlowInEasing,
            ),
            label = "animatedListItem",
        )
        Box(
            Modifier.graphicsLayer {
                alpha = progress
                val scale = 0.7f + 0.3f * progress
                scaleX = scale
                scaleY = scale
            }
        ) { content() }
    }
}

/** 成员详情弹窗：头像、昵称、所属分组与分工；有 detail 的成员再补一段贡献说明。
 *  show 与 focus 分开传：关闭后 focus 仍保留最后一次选择，供退场动画期间继续渲染内容。 */
@Composable
internal fun MemberDetailDialog(show: Boolean, focus: MemberFocus?, onDismiss: () -> Unit) {
    val contentHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 160.dp).coerceAtLeast(180.dp)
    OverlayDialog(show = show, title = "成员信息", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = contentHeight).testTag("member_dialog_content")) {
            focus?.let { current ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QqAvatar(current.member.qq, size = 76.dp, spec = 640)
                    Text(current.member.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp))
                    Text(current.group, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp))
                    HorizontalDivider(Modifier.padding(vertical = 16.dp),
                        color = colorScheme.onSurface.copy(alpha = 0.08f))
                    MemberInfoRow("分工", current.member.role)
                }
                current.member.detail?.let { detail ->
                    // 详情可能有多行（作者的贡献说明是分条的），限高后可滚动，不挤走下面的关闭按钮。
                    Text(detail, fontSize = 13.sp, lineHeight = 21.sp,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp))
                }
                TextButton("关闭", onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
            }
        }
    }
}

@Composable
private fun MemberInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(48.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
