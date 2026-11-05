package io.github.currencortex.music.feature.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

internal object AndroidPasskeyLogin {
    fun origin(context: Context): String {
        if (Build.VERSION.SDK_INT < 28) throw ApiException(ErrorKind.Forbidden,
            userMessage = "通行密钥需要 Android 9 或更高版本")
        @Suppress("DEPRECATION")
        val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners.orEmpty()
        require(signatures.size == 1)
        val hash = MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
        return "android:apk-key-hash:" + Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    suspend fun credential(context: Context, requestJson: String): String {
        try {
            // Origin is assigned by Android from the actual APK signature. Never impersonate a web origin.
            val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
            val result = CredentialManager.create(context).getCredential(context, request).credential
            return (result as? PublicKeyCredential)?.authenticationResponseJson
                ?: throw ApiException(ErrorKind.Parse, userMessage = "系统未返回通行密钥，请重试")
        } catch (e: GetCredentialCancellationException) {
            throw CancellationException("Passkey selection cancelled")
        } catch (e: NoCredentialException) {
            throw ApiException(ErrorKind.NotFound, userMessage = "未找到可用的通行密钥，请检查凭据管理器或使用其他登录方式")
        } catch (e: GetCredentialProviderConfigurationException) {
            throw ApiException(ErrorKind.Forbidden, userMessage = "此设备未配置可用的凭据管理器，请使用其他登录方式")
        } catch (e: GetCredentialUnsupportedException) {
            throw ApiException(ErrorKind.Forbidden, userMessage = "此设备暂不支持通行密钥，请使用其他登录方式")
        } catch (e: GetCredentialException) {
            throw ApiException(ErrorKind.Unknown, userMessage = "无法使用通行密钥，请确认服务器已完成 App 关联，或使用其他登录方式")
        }
    }
}
