package io.github.currencortex.music.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.os.Build
import androidx.room.Room
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.download.DownloadCover
import io.github.currencortex.music.core.download.SongTagWriter
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.local.*
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.domain.FoliaLyricsMatchPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Only performance APKs contain this entry/fixtures. Calls production APIs after R8. */
object MinifiedRuntimeSmoke {
    suspend fun run(context: Context, container: AppContainer): String {
        check(context.packageName == "com.bileizhen.currentmusic.benchmark") { "Runtime smoke requires isolated performance package" }
        container.ready.await()
        return withContext(Dispatchers.IO) {
            val evidence = mutableListOf("MINIFIED_RUNTIME_SMOKE sdk=${Build.VERSION.SDK_INT}")
            fun fixture(name: String): JsonObject = context.assets.open("runtime-smoke/$name")
                .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }

            // Executes bundled wanakana + OpenCC inside the actual production Rhino interpreter.
            check(FoliaLyricsMatchPolicy.normalize("さよならの夏 - Café!") == "sayonarano夏 café")
            check(FoliaLyricsMatchPolicy.normalize("無盡的愛") == "无尽的爱")
            check(FoliaLyricsMatchPolicy.normalize("안녕, мир?") == "안녕 мир")
            evidence += "PASS Folia/Rhino kana, traditional Chinese, Unicode normalization"

            val ai = OriginalAiMasterCodec(context.assets.open("netease-ai-master-codec.js").use { it.readBytes() })
            val base = fixture("original-ai-master/base.json")
            val diy = fixture("original-ai-master/diy.json")
            fun gains(value: JsonObject) = value["eq"]!!.jsonObject["eqs"]!!.jsonArray.map { it.jsonPrimitive.double }
            check(base.size == 32)
            base.forEach { (key, value) ->
                val result = ai.compose(base, diy, key, null)
                check(gains(value.jsonObject["effect"]!!.jsonObject) ==
                    gains(Json.parseToJsonElement(result.json).jsonObject["aev2"]!!.jsonObject))
                check(NeteasePresetParameters.fromJson(result.json).usesAev2)
            }
            val songPlan = ai.compose(base, diy, "key_warm", fixture("original-ai-master/song.json"))
            check(songPlan.songSpecific && NeteasePresetParameters.fromJson(songPlan.json).usesAev2)
            check(ai.compose(base, diy, "key_transparent", null, OriginalAiMasterCodec.DIY_KEYS).usedDiy == listOf("en_treble", "en_vibe"))
            evidence += "PASS AI Master/Rhino 32 original bases, song override, DIY JSON"

            val mood = OriginalMoodCodec(context.assets.open("netease-original/mood-codec.js").use { it.readBytes() })
            val inputs = listOf(MoodTensorInfo("emotion_idx", 0, 32, listOf(1)), MoodTensorInfo("genre_idx", 0, 32, listOf(1)),
                MoodTensorInfo("features", 2, 32, listOf(1, 64)), MoodTensorInfo("base_extra_flat", 2, 32, listOf(1, 32)))
            val outputs = listOf(MoodTensorInfo("aev2", 2, 32, listOf(1, 32)))
            val prepared = mood.prepare(fixture("original-mood/descriptor.json"), inputs, outputs)
            check(prepared.infer && prepared.emotion == 11 && prepared.genre == 0)
            val golden = fixture("original-mood/golden.json")
            val expectedTensors = golden["prepared"]!!.jsonObject["tensors"]!!.jsonArray.associate {
                it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["data"]!!.jsonArray.map { value -> value.jsonPrimitive.double }
            }
            prepared.value["tensors"]!!.jsonArray.forEach {
                check(expectedTensors[it.jsonObject["name"]!!.jsonPrimitive.content] ==
                    it.jsonObject["data"]!!.jsonArray.map { value -> value.jsonPrimitive.double })
            }
            val finished = mood.finish(prepared, prepared.extra)
            check(Json.parseToJsonElement(finished.json).jsonObject["aev2"] == golden["finished"]!!.jsonObject["aev2"])
            check(finished.emotion == "nostalgic")
            val template = mood.prepare(buildJsonObject { put("song_id", 1) }, inputs, outputs)
            check(!template.infer && !mood.finish(template, null).inferred)
            evidence += "PASS Mood/Rhino preprocessing + supplied-tensor postprocessing golden; MNN inference not exercised"

            val fixtureSong = Song(91234567, "R8 测试 / Café", "歌手 · さよなら", "离线 fixture", durationMs = 600,
                artistIds = listOf(11, 22), musicSource = MusicSource.QQ_MUSIC,
                externalIds = SongExternalIds(neteaseId = "123", qqMusicId = "smoke"))
            val encoded = ApiJson.encodeToString(fixtureSong)
            check(ApiJson.decodeFromString<Song>(encoded) == fixtureSong)
            check(ApiJson.decodeFromString<SongDto>("""{"ncm_id":123,"name":"Unicode 海","artist_ids":[11]}""").toDomain().id == 123L)
            val database = Room.inMemoryDatabaseBuilder(context.applicationContext, MusicDatabase::class.java).build()
            try {
                database.music().saveQueue(QueueSnapshotEntity(payload = encoded))
                val restored = checkNotNull(database.music().queue()).payload
                check(ApiJson.decodeFromString<Song>(restored) == fixtureSong)
                database.music().cacheLyrics(CachedLyrics(91234567, "[00:00.000]测试", "runtime-smoke"))
                check(database.music().lyrics(91234567, "runtime-smoke")?.payload == "[00:00.000]测试")
            } finally { database.close() }
            evidence += "PASS Room generated database/DAO + production serialization nested models/wire names"

            if (BuildConfig.NETEASE_ORIGINAL_EFFECTS && "arm64-v8a" in Build.SUPPORTED_ABIS) {
                NeteaseNativeEngine.configure(context)
                check(NeteaseNativeEngine.available()) { "Bundled original JNI engine failed to load after R8" }
                val preset = NeteaseOriginalPreset(0, "Synthetic smoke", 1, """{"eq":{"on":false}}""".toByteArray())
                NeteaseNativeEngine(48000, 2, preset).use { engine ->
                    val pcm = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
                    val output = ByteBuffer.allocateDirect(32768)
                    var produced = 0
                    repeat(8) {
                        pcm.clear(); repeat(1024) { sample -> pcm.putShort(if (sample % 4 < 2) 1000 else -1000) }; pcm.flip()
                        output.clear()
                        val count = engine.process(pcm, output)
                        check(count in 0..output.capacity() && count % 4 == 0)
                        produced += count
                    }
                    check(produced > 0) { "Original JNI did not produce PCM" }
                }
                check(NeteaseEngineLibraries.moodAvailable())
                OriginalMoodModel.loadLibraries() // JNI_OnLoad registers the complete version-bound MNN ABI.
                val tensor = com.netease.cloudmusic.core.mnn.MNNTensorInfo("fixture", 2, 32, intArrayOf(1, 32))
                check(tensor.name == "fixture" && tensor.typeBits == 32 && tensor.shape.contentEquals(intArrayOf(1, 32)))
                evidence += "PASS original JNI create/process/release + MNN libraries/ABI load; no model inference or speaker output"
            } else evidence += "SKIP bundled native/MNN: unavailable ABI or build opt-out; not verified"

            val temp = File.createTempFile("runtime-smoke-", ".tmp", context.cacheDir)
            check(temp.delete() && temp.mkdir())
            try {
                val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff267af5.toInt()) }
                val cover = try { ByteArrayOutputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)); DownloadCover(stream.toByteArray(), 8, 8)
                } } finally { bitmap.recycle() }
                for (extension in listOf("mp3", "flac", "m4a")) {
                    val file = File(temp, "fixture.$extension")
                    context.assets.open("runtime-smoke/media/fixture.$extension").use { input -> file.outputStream().use(input::copyTo) }
                    val before = audioHash(file)
                    val lyrics = "[00:00.000]R8 测试 / Café"
                    SongTagWriter.write(file, fixtureSong, lyrics, cover)
                    val tag = AudioFileIO.read(file).tag
                    check(tag.getFirst(FieldKey.TITLE) == fixtureSong.name)
                    check(tag.getFirst(FieldKey.ARTIST) == fixtureSong.artists)
                    check(tag.getFirst(FieldKey.ALBUM) == fixtureSong.album)
                    check(tag.getFirst(FieldKey.LYRICS) == lyrics)
                    check(tag.firstArtwork?.binaryData?.contentEquals(cover.bytes) == true)
                    check(before.contentEquals(audioHash(file))) { "Tagging changed encoded media in $extension" }
                }
                evidence += "PASS MP3/FLAC/AAC-in-M4A Unicode tags/lyrics/JPEG write/read + identical encoded sample SHA256"
            } finally {
                // Only this invocation's freshly-created private fixture directory is removable.
                check(temp.parentFile == context.cacheDir && temp.name.startsWith("runtime-smoke-"))
                temp.deleteRecursively()
            }
            evidence += "No network/account writes; DAC/Bluetooth/acoustic output and model inference remain unverified"
            evidence.joinToString("\n")
        }
    }

    private fun audioHash(file: File): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            check(extractor.trackCount > 0)
            extractor.selectTrack(0)
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(1024 * 1024)
            var samples = 0
            while (true) {
                buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                check(count > 0)
                buffer.position(0); buffer.limit(count); digest.update(buffer)
                samples++; extractor.advance()
            }
            check(samples > 0) { "Fixture has no encoded audio samples" }
            return digest.digest()
        } finally { extractor.release() }
    }
}
