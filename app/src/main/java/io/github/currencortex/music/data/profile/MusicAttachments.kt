package io.github.currencortex.music.data.profile

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun validMusicAttachments(cards: List<PostAttachment>): List<PostAttachment> = cards.filter {
    it.type in setOf("song", "album", "artist", "playlist") && it.id > 0 && it.name.isNotBlank()
}.distinctBy { it.type to it.id }.take(5).map { card ->
    val url = card.pic.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
    card.copy(name = card.name.take(120), sub = card.sub.take(120), pic = url?.toString().orEmpty())
}
