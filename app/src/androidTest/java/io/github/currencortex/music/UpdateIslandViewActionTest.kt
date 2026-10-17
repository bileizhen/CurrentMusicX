package io.github.currencortex.music

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.update.UpdateInstallAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Use the real cold/warm entry points; viewing must never request package installation. */
class UpdateIslandViewActionTest {
    @Test fun readyIslandTapOpensTheUpdatePageOnColdAndWarmEntry(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        val updates = app.container.updates
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(app, MainActivity::class.java).putExtra(UpdateInstallAction.EXTRA, true))
        try {
            withTimeout(10_000) { updates.dialogVisible.first { it } }
            scenario.onActivity {
                assertFalse(app.container.updateTransfer.state.value.installing)
                assertFalse(it.intent.hasExtra(UpdateInstallAction.EXTRA))
                updates.dismissDialog()
                UpdateInstallAction.open(it)
            }
            withTimeout(10_000) { updates.dialogVisible.first { it } }
            scenario.onActivity { assertFalse(app.container.updateTransfer.state.value.installing) }
        } finally {
            updates.dismissDialog()
            scenario.close()
        }
    }
}
