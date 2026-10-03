package io.github.currencortex.music.core.network

val ApiJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
