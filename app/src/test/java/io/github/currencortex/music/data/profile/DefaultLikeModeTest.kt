package io.github.currencortex.music.data.profile

import io.github.currencortex.music.data.settings.LikeMode
import org.junit.Assert.*
import org.junit.Test

class DefaultLikeModeTest {
    @Test fun automaticAndExplicitNeteaseFallBackOnlyWhenUnbound() {
        assertTrue(LikeMode.AUTO.useNetease(true)); assertFalse(LikeMode.AUTO.useNetease(false))
        assertTrue(LikeMode.NETEASE.useNetease(true)); assertFalse(LikeMode.NETEASE.useNetease(false))
        assertFalse(LikeMode.CURRENT_MUSIC.useNetease(true)); assertFalse(LikeMode.CURRENT_MUSIC.useNetease(false))
        assertEquals(LikeMode.AUTO, LikeMode.from("unknown"))
    }
}
