package io.github.currencortex.music.core.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureVisibilityRegistryTest {
    @Test fun closingPreviewKeepsVisiblePlayerCapture() {
        val registry = CaptureVisibilityRegistry()
        val player = Any(); val preview = Any()
        assertEquals(true to true, registry.update(player, true, true))
        registry.update(preview, true, true)
        assertEquals(true to true, registry.update(preview, false, false))
        assertEquals(false to false, registry.update(player, false, false))
    }
    @Test fun permissionBelongsOnlyToVisibleOwners() {
        val registry = CaptureVisibilityRegistry()
        val allowed = Any(); val denied = Any()
        registry.update(allowed, true, true)
        registry.update(denied, true, false)
        assertEquals(true to false, registry.update(allowed, false, true))
        assertEquals(true to true, registry.update(denied, true, true))
    }
}
