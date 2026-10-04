package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Each fixture owns a distinct vault, primary token, DataStore and Room database. */
class UserCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var namespace: String
    @Volatile private var nickname = "First user"
    @Volatile private var decoration = ""
    @Volatile private var bound = false
    @Volatile private var sentPhone = ""
    @Volatile private var sentCode = ""
    @Volatile private var uploadedAvatar: ByteArray? = null
    private val qrChecks = AtomicInteger()
    @Volatile private var failNextPhoneLogin = false
    private val syncRequests = AtomicInteger()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun user(id: Int = 7): String = """{"id":$id,"username":"user$id","nickname":"${if (id == 7) nickname else "Second user"}","bio":"Profile bio","avatarDecoration":"$decoration","publicSquare":true,"stat":{"likes":3,"favs":2,"playDays":5,"listenMs":7200000}}"""
    @Before fun prepare() = runBlocking {
        namespace = "user-test-${UUID.randomUUID()}"
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), namespace)
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/fixture/frame.gif" -> MockResponse().setHeader("Content-Type", "image/gif")
                        .setBody(okio.Buffer().write(java.util.Base64.getDecoder().decode(
                            "R0lGODlhCAAIAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAACAAIAAAIDwABCBxIsKDBgwgTKkwYEAAh+QQBCgABACwAAAAACAAIAIEAAP8AAAAAAAAAAAAIDwABCBxIsKDBgwgTKkwYEAA7")))
                    path == "/cm/auth/me" -> json(user(if (request.getHeader("Authorization") == "Bearer isolated-second-token") 8 else 7))
                    path == "/cm/auth/login" -> json("""{"token":"isolated-second-token","user":${user(8)}}""")
                    path == "/cm/profile" -> { nickname = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["nickname"]!!.jsonPrimitive.content; json("{}") }
                    path == "/cm/profile/avatar" -> { uploadedAvatar = java.util.Base64.getDecoder().decode(ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["data"]!!.jsonPrimitive.content); json("{}") }
                    path == "/cm/decorations/mine" -> { decoration = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["id"]!!.jsonPrimitive.content; json("{}") }
                    path == "/cm/decorations" -> json("""{"unlocked":true,"listenMs":7200000,"minListenMs":7200000,"current":"$decoration","decorations":[{"id":"frame-one","name":"Frame one"}],"scales":{"frame-one":1.4}}""")
                    path == "/cm/decorations/scales" -> json("""{"scales":{"frame-one":1.4}}""")
                    path == "/cm/users/square" -> {
                        assertNull(request.getHeader("Authorization"))
                        json("""{"total":1,"users":[{"id":9,"nickname":"Public user","bio":"Public bio","days":5}],"stats":{"users":1,"listening":0}}""")
                    }
                    path == "/cm/users/9/profile" -> json("""{"user":{"id":9,"nickname":"Public user","bio":"Public bio"},"stat":{"listenMs":60000}}""")
                    path == "/cm/daily" -> json("""{"daily":[],"forYou":[]}""")
                    path == "/cm/playlists" -> json("""{"playlists":[]}""")
                    path == "/cm/plays/recent" -> json("""{"songs":[]}""")
                    path == "/cm/ncmbind" && request.method == "DELETE" -> { bound = false; json("{}") }
                    path == "/cm/ncmbind" -> json("""{"bound":$bound,"profile":{"nickname":"Cloud user"}}""")
                    path == "/cm/ncmbind/qr/key" -> json("""{"key":"isolated-qr-key"}""")
                    path == "/cm/ncmbind/qr/check" -> { qrChecks.incrementAndGet(); json("""{"code":801}""") }
                    path == "/cm/ncmbind/phone/login" -> {
                        if (failNextPhoneLogin) {
                            failNextPhoneLogin = false
                            return MockResponse().setResponseCode(502).setBody("""{"error":"upstream failure","captcha":"private-code"}""")
                        }
                        val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                        sentPhone = body["phone"]!!.jsonPrimitive.content; sentCode = body["captcha"]!!.jsonPrimitive.content
                        bound = true; json("{}")
                    }
                    path == "/cm/ncmbind/sync" -> { syncRequests.incrementAndGet(); json("""{"imported":2,"tracks":10,"pending":1}""") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-first-token", UserDto(7, "user7", "First user"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { io.github.currencortex.music.data.settings.AppearanceSettings(blur = false) }
    }
    @After fun finish() = runBlocking {
        if (::container.isInitialized) {
            container.accountRepository.clear()
            container.accountRepository.savedAccounts.value.forEach { container.accountRepository.removeSaved(it.key) }
            container.close()
        }
        if (::server.isInitialized) server.shutdown()
    }
    private fun me() {
        compose.onNodeWithText("我的").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("edit_profile").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun installedImagePipelineDecodesAnimatedGifOverHttp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val result = coil3.SingletonImageLoader.get(context).execute(coil3.request.ImageRequest.Builder(context)
            .data(server.url("/fixture/frame.gif").toString()).size(64).build())
        assertTrue("GIF must decode successfully", result is coil3.request.SuccessResult)
        val image = (result as coil3.request.SuccessResult).image
        assertTrue("GIF must retain animation", (image as? coil3.DrawableImage)?.drawable is android.graphics.drawable.Animatable)
        assertNull("Images must not carry account credentials", server.takeRequest().getHeader("Authorization"))
    }
    @Test fun ownProfileEditsAndPublicProfilesRespectOwnershipAndRestoreNavigation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("首页").fetchSemanticsNodes().isNotEmpty() }
        me()
        compose.onNodeWithText("2 小时 0 分").assertExists()
        compose.onNodeWithTag("edit_profile").performClick()
        compose.onNodeWithTag("profile_nickname").performTextReplacement("Updated user")
        compose.onNodeWithTag("save_profile").performClick()
        compose.waitUntil(10000) { container.accountRepository.state.value.account?.nickname == "Updated user" }
        compose.waitUntil(10000) { compose.onAllNodesWithText("关闭").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("发现").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("discover_user_9").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("discover_user_9").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Public user").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit_profile").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Public user").assertExists()
    }
    @Test fun multipleAccountsSwitchWithoutPlaintextCredentialsInMetadata() {
        compose.setContent { CurrentMusicApp(container) }; me()
        compose.onNodeWithTag("open_accounts").performScrollTo().performClick()
        compose.onNodeWithText("添加账号").performClick()
        compose.onNodeWithTag("login_username").performTextInput("user8")
        compose.onNodeWithTag("login_password").performTextInput("sample-password")
        compose.onNodeWithTag("login_submit").performClick()
        compose.waitUntil(10000) { container.accountRepository.state.value.account?.id == 8L &&
            compose.onAllNodesWithTag("login_submit").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("switch_account_7").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("switch_account_7").performScrollTo().performClick()
        compose.waitUntil(10000) { container.accountRepository.state.value.account?.id == 7L && !container.accountRepository.state.value.loading }
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val preferences = context.preferencesDataStoreFile("app.$namespace.preferences_pb").readBytes().toString(Charsets.UTF_8)
        assertFalse(preferences.contains("isolated-first-token")); assertFalse(preferences.contains("isolated-second-token")); assertFalse(preferences.contains("sample-password"))
        val credentials = context.noBackupFilesDir.listFiles()!!.filter { it.name.contains(namespace) && it.name.endsWith(".aes") }
        assertTrue(credentials.size >= 3)
        credentials.forEach { file -> assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("isolated-")) }
    }
    @Test fun decorationsPreviewSearchAndWearUseServerCatalog() {
        compose.setContent { CurrentMusicApp(container) }; me()
        compose.onNodeWithTag("open_decorations").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("decoration_frame-one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("decoration_query").performTextInput("frame-one")
        compose.onNodeWithTag("decoration_frame-one").performClick()
        compose.onNodeWithTag("wear_decoration").performClick()
        compose.waitUntil(10000) { decoration == "frame-one" }
        compose.waitUntil(10000) { compose.onAllNodesWithText("挂件已设置").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("取消佩戴").performClick()
        compose.waitUntil(10000) { decoration.isEmpty() }
    }
    @Test fun avatarPreviewAndUploadCompressTheSelectedImageInMemory() {
        lateinit var profileVm: io.github.currencortex.music.feature.profile.ProfileViewModel
        compose.setContent {
            profileVm = androidx.lifecycle.viewmodel.compose.viewModel(key = "my-profile", factory =
                io.github.currencortex.music.ui.util.viewModelFactory { io.github.currencortex.music.feature.profile.ProfileViewModel(container) })
            CurrentMusicApp(container)
        }
        me()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = java.io.File(context.cacheDir, "avatar-$namespace.png")
        val bitmap = android.graphics.Bitmap.createBitmap(2048, 1024, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnIdle { profileVm.avatar.value = android.net.Uri.fromFile(file); profileVm.dialog.value = "avatar" }
            compose.onNodeWithTag("confirm_avatar_upload").performClick()
            compose.waitUntil(10000) { uploadedAvatar != null }
            val bytes = uploadedAvatar!!
            assertTrue(bytes.size <= 1024 * 1024)
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            assertTrue(options.outWidth <= 1024 && options.outHeight <= 1024)
            assertEquals("image/jpeg", options.outMimeType)
        } finally { bitmap.recycle(); file.delete() }
    }
    @Test fun qrPollingStopsOnExitAndPhoneBindingSyncAndUnbindWork() {
        lateinit var bindingVm: io.github.currencortex.music.feature.binding.BindingViewModel
        compose.setContent {
            bindingVm = androidx.lifecycle.viewmodel.compose.viewModel(key = "user/binding", factory =
                io.github.currencortex.music.ui.util.viewModelFactory { io.github.currencortex.music.feature.binding.BindingViewModel(container) })
            CurrentMusicApp(container)
        }
        me()
        compose.onNodeWithTag("open_binding").performScrollTo().performClick()
        compose.onNodeWithTag("qr_binding").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("binding_qr").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) { qrChecks.get() > 0 }
        compose.onNodeWithText("返回").performScrollTo().performClick()
        compose.waitForIdle(); val checks = qrChecks.get()
        Thread.sleep(2300); assertEquals(checks, qrChecks.get())
        compose.onNodeWithTag("open_binding").performScrollTo().performClick()
        compose.onNodeWithText("手机验证码").performScrollTo().performClick()
        compose.onNodeWithTag("binding_phone").performScrollTo().performTextInput("123456789")
        compose.onNodeWithTag("binding_code").performScrollTo().performTextInput("1234")
        compose.onNodeWithTag("binding_code").performImeAction()
        failNextPhoneLogin = true
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasTestTag("confirm_phone_binding"))
        compose.onNodeWithTag("confirm_phone_binding").performClick()
        val failure = "网易云手机验证码登录暂时失败（HTTP 502），请稍后重试，或改用扫码登录。"
        compose.waitUntil(10000) { bindingVm.message.value == failure }
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasText(failure))
        compose.onNodeWithText(failure).assertExists()
        assertEquals(7L, container.accountRepository.state.value.account?.id)
        assertFalse(bound); assertEquals(0, syncRequests.get())
        compose.onNodeWithTag("binding_code").performScrollTo().performTextInput("1234")
        compose.onNodeWithTag("binding_code").performImeAction()
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasTestTag("confirm_phone_binding"))
        compose.onNodeWithTag("confirm_phone_binding").performClick()
        val success = "绑定成功；已同步 2 个歌单 / 10 首歌曲，1 个待续传"
        compose.waitUntil(10000) { bindingVm.message.value == success }
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasText(success))
        compose.onNodeWithText(success).assertExists()
        assertEquals("123456789", sentPhone); assertEquals("1234", sentCode)
        compose.onNodeWithText("解绑").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_unbind").performClick()
        compose.waitUntil(10000) { !bound }
    }
}
