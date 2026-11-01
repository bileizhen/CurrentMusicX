package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlin.math.abs
import java.io.File

/** The colored pane inside upstream's .67dp backdrop hairline follows one continuous capsule. */
class GlassButtonOutlineTest {
    @get:Rule val compose=createComposeRule()
    private fun checkContour(dark:Boolean) {
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT)) {
            val backdrop=rememberGlassBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier).background(Color.Black))
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    GlassActionButton("",{},Modifier.width(160.dp).testTag("optical_pill"),primary=true,backdrop=backdrop)
                    Box(Modifier.size(160.dp,44.dp).testTag("native_pill").drawBehind {
                        val line=.67.dp.toPx()
                        drawRoundRect(Color.White,Offset(line,line),Size(size.width-2*line,size.height-2*line),
                            CornerRadius(size.height/2-line))
                    })
                }
            }
        }}
        compose.waitForIdle()
        val optical=compose.onNodeWithTag("optical_pill").fetchSemanticsNode().boundsInRoot
        val native=compose.onNodeWithTag("native_pill").fetchSemanticsNode().boundsInRoot
        assertEquals(native.width,optical.width,0f);assertEquals(native.height,optical.height,0f)
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.externalCacheDir,"button-contour-${if(dark)"dark" else "light"}.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        fun blue(x:Int,y:Int)=android.graphics.Color.blue(image.getPixel(x,y)).toFloat()
        val body=blue(optical.center.x.toInt(),optical.center.y.toInt())
        var worst=0f;var worstPoint="";var mismatches=0
        try {
            val w=native.width.toInt();val h=native.height.toInt()
            // Include the capsule-to-straight-side joins and both endcaps, where the
            // duplicated masks change fractional coverage into a visibly stepped edge.
            for(y in 0 until h)for(x in 0 until w) {
                if(y>=5&&y<h-5&&x>=5&&x<w-5)continue
                val expected=blue(native.left.toInt()+x,native.top.toInt()+y)/255f
                val actual=blue(optical.left.toInt()+x,optical.top.toInt()+y)/body
                val error=abs(expected-actual.coerceIn(0f,1f))
                if(error>worst){worst=error;worstPoint="$x,$y expected=$expected actual=$actual"}
                if(error>.12f)mismatches++
            }
        }finally{image.recycle()}
        assertEquals("The colored contour must follow the same inset around straight sides and endcaps; dark=$dark worst=$worst at $worstPoint",0,mismatches)
    }
    @Test fun darkPillHasOneContinuousAntialiasedContour()=checkContour(true)
    @Test fun lightPillHasOneContinuousAntialiasedContour()=checkContour(false)
}
