package io.github.currencortex.music

import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Actual display clock and native input, separate from the deterministic Compose clock tests. */
class GlassPopupFramePacingTest {
    @Test fun profileReturnTailOnTheUpdatePage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("glassFrameProfile")=="true")
        val submenu=InstrumentationRegistry.getArguments().getString("glassSubmenuProfile")=="true"
        val trace=InstrumentationRegistry.getArguments().getString("glassMethodTrace")=="true"
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val scenario=ActivityScenario.launch(ComponentActivity::class.java)
        val worker=HandlerThread("glass-return-metrics").apply{start()}
        val cycle=AtomicInteger(-1)
        val closedAt=AtomicLong(0)
        val frames=Collections.synchronizedList(mutableListOf<List<Long>>())
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,dropped->
            val i=cycle.get()
            val elapsed=(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)-closedAt.get())/1_000_000
            if(i>=0&&elapsed in 0..750)frames+=listOf(i.toLong(),elapsed,dropped.toLong(),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION),metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),metrics.getMetric(FrameMetrics.GPU_DURATION),
                metrics.getMetric(FrameMetrics.ANIMATION_DURATION),metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),
                metrics.getMetric(FrameMetrics.SYNC_DURATION),metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
        }
        fun findNode(node:AccessibilityNodeInfo?,label:String):Rect? {
            if(node==null)return null
            if(node.contentDescription?.toString()?.contains(label)==true||node.text?.toString()==label)return Rect().also{node.getBoundsInScreen(it)}
            repeat(node.childCount){findNode(node.getChild(it),label)?.let{return it}}
            return null
        }
        fun tap(rect:Rect) {
            val down=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0).let{
                    instrumentation.uiAutomation.injectInputEvent(it,true);it.recycle()}
                Thread.sleep(20)
            }
        }
        try {
            scenario.onActivity {activity->
                activity.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper))
                activity.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.DARK)) {Scaffold {
                    val fixture=AppRelease("1.2.6","","https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.2.6",
                        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.2.6/app.apk","fixture.apk",false,1024)
                    UpdatePageContent(if(submenu)UpdateState.Available(fixture)else UpdateState.UpToDate,
                        sourceCheck=UpdateSourceCheck{_,_->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,125)})
                }}}
            }
            Thread.sleep(1400)
            val automation=instrumentation.uiAutomation
            automation.serviceInfo=automation.serviceInfo.apply {
                flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            var found:Rect?=null
            repeat(30){if(found==null){found=findNode(automation.rootInActiveWindow,"更多");Thread.sleep(100)}}
            val source=checkNotNull(found){"Missing actual update-page menu trigger; root=${automation.rootInActiveWindow}"}
            repeat(4){i->
                cycle.set(-1)
                if(!submenu||i==0) {tap(source);Thread.sleep(650)}
                if(submenu) {
                    tap(checkNotNull(findNode(automation.rootInActiveWindow,"下载源")))
                }
                Thread.sleep(750)
                if(trace&&i==1)Debug.startMethodTracingSampling(File(instrumentation.targetContext.externalCacheDir,"glass-submenu.trace").absolutePath,32*1024*1024,1000)
                closedAt.set(System.nanoTime());cycle.set(i)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                Thread.sleep(800)
                if(trace&&i==1)Debug.stopMethodTracing()
            }
            cycle.set(-1)
            val captured=synchronized(frames){frames.toList()}
            val tail=captured.filter{it[1] in (if(submenu)150..550 else 300..650)}.map{it[3]/1_000_000.0}.sorted()
            check(tail.size>15){"No real display-clock tail sample"}
            val summary="tailFrames=${tail.size}, p95Ms=${tail[((tail.size-1)*.95).toInt()]}, maxMs=${tail.last()}, over33Ms=${tail.count{it>33.333}}"
            File(instrumentation.targetContext.externalCacheDir,if(submenu)"glass-submenu-real-frames.csv"else "glass-popup-real-frames.csv").writeText(
                "cycle,closeElapsedMs,dropped,totalNs,drawNs,layoutNs,gpuNs,animationNs,unknownDelayNs,syncNs,commandNs,swapNs\n"+captured.joinToString("\n"){it.joinToString(",")})
            instrumentation.sendStatus(0,Bundle().apply{putString("stream","\nGLASS_${if(submenu)"SUBMENU_"else ""}RETURN_REAL_CLOCK $summary\n")})
        }finally{
            scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
            scenario.close();worker.quitSafely()
        }
    }
}
