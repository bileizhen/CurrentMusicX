// Installer checks adapted from 123PanX; GPL-3.0-only.
package io.github.currencortex.music.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.currencortex.music.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UpdateInstallResult { STARTED, PERMISSION_REQUIRED }
interface UpdateInstall {
    fun canInstall(): Boolean
    suspend fun request(file: File, release: AppRelease): UpdateInstallResult
}

class AndroidUpdateInstaller(private val context: Context) : UpdateInstall {
    override fun canInstall() = context.packageManager.canRequestPackageInstalls()

    @Suppress("DEPRECATION")
    override suspend fun request(file: File, release: AppRelease): UpdateInstallResult {
        withContext(Dispatchers.IO) {
            require(UpdateDownloader.matches(file, release)) { "安装包校验失败，请重新下载" }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: error("无法读取安装包")
            require(archive.packageName == RELEASE_PACKAGE && archive.versionName == release.version) { "安装包与发布版本不匹配" }
            require(signatures(archive).isNotEmpty()) { "安装包未签名" }
            if (context.packageName == RELEASE_PACKAGE) {
                val installed = context.packageManager.getPackageInfo(context.packageName, flags)
                require(signatures(archive) == signatures(installed)) { "安装包签名与当前应用不同" }
                val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
                require(code > BuildConfig.VERSION_CODE) { "安装包版本代码未递增" }
            }
        }
        return withContext(Dispatchers.Main) {
            if (!canInstall()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                UpdateInstallResult.PERMISSION_REQUIRED
            } else {
                context.startActivity(installIntent(context, file))
                UpdateInstallResult.STARTED
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { it.toCharsString() }?.toSet().orEmpty()

    companion object {
        val RELEASE_PACKAGE = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
        fun installIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", file), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
