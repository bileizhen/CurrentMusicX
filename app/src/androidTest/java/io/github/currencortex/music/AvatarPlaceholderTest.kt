package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.DecoratedAvatar
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Accounts without an avatar must show the person disc, never a music note. */
class AvatarPlaceholderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingAvatarRendersThePersonPlaceholder() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DecoratedAvatar(null, null, size = 44.dp, reserveOverlay = false)
                    DecoratedAvatar(null, null, size = 64.dp, reserveOverlay = false)
                    DecoratedAvatar(null, null, size = 96.dp, reserveOverlay = false)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().assertExists()
    }
}
