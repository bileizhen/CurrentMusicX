package io.github.currencortex.music.core.visualizer

/** Independent UI owners cannot stop each other's capture when a window is disposed. */
class CaptureVisibilityRegistry {
    private val owners = mutableMapOf<Any, Boolean>()
    @Synchronized fun update(owner: Any, visible: Boolean, permission: Boolean): Pair<Boolean, Boolean> {
        if (visible) owners[owner] = permission else owners.remove(owner)
        return owners.isNotEmpty() to owners.values.any { it }
    }
}
