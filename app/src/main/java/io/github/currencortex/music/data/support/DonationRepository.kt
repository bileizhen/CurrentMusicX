package io.github.currencortex.music.data.support

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal

@Serializable data class DonationProfile(val username: String, val nickname: String? = null,
    val email: String? = null, val phone: String? = null, val created_at: Long? = null)
@Serializable data class DonationRecord(val id: Long, val amount_cents: Long, val note: String? = null,
    val txn_id: String, val created_at: Long)
@Serializable data class DonationHistory(val items: List<DonationRecord> = emptyList(),
    val count: Int = 0, val totalCents: Long = 0)
@Serializable data class DonationInfo(val profile: DonationProfile, val noteMax: Int = 120,
    val txnMax: Int = 64, val mine: DonationHistory = DonationHistory()) {
    val noteLimit get() = noteMax.coerceIn(0, 120)
    val transactionLimit get() = txnMax.coerceIn(6, 64)
}
@Serializable private data class DonationResponse(val ok: Boolean, val mine: DonationHistory)

/** Amounts stay decimal from input through JSON; a submitted amount is self-reported. */
data class DonationInput(val amount: String = "", val transaction: String = "", val note: String = "") {
    fun validation(info: DonationInfo): String? = when {
        !amount.trim().matches(Regex("[0-9]{1,6}(\\.[0-9]{1,2})?")) -> "请输入有效金额，最多两位小数"
        amount.trim().toBigDecimal() !in BigDecimal("0.01")..BigDecimal("100000") -> "金额须在 0.01 至 100000 元之间"
        !transaction.trim().matches(Regex("[A-Za-z0-9._-]{6,${info.transactionLimit}}")) ->
            "交易单号须为 6 至 ${info.transactionLimit} 位数字、字母或 ._-"
        note.length > info.noteLimit -> "备注最多 ${info.noteLimit} 字"
        else -> null
    }
}

interface DonationApi {
    suspend fun info(owner: RequestSession): DonationInfo
    suspend fun qr(owner: RequestSession): ByteArray
    suspend fun submit(owner: RequestSession, input: DonationInput, info: DonationInfo): DonationHistory
}

class DonationRepository(private val api: ApiClient,
    private val images: OkHttpClient = ApiClient.defaultHttpClient()) : DonationApi {
    override suspend fun info(owner: RequestSession): DonationInfo = api.decode(api.request("GET", "donation/info",
        authenticated = true, expectedSession = owner, projectOnly = true))

    override suspend fun qr(owner: RequestSession): ByteArray {
        // Fixed public path on the selected server: never attach an account token to the image.
        val url = ServerUrl.endpoint(api.routeServer(owner.server), "donation/qr", emptyMap())
        return images.newCall(Request.Builder().url(url).header("Accept", "image/jpeg").build()).readResponse { response ->
            if (!response.isSuccessful) throw ApiException(if (response.code == 404) ErrorKind.NotFound else ErrorKind.Server, response.code)
            val body = response.body ?: throw ApiException(ErrorKind.Parse)
            val limit = 5L * 1024 * 1024
            if (body.contentType()?.let { it.type == "image" && it.subtype == "jpeg" } != true ||
                body.contentLength() > limit) throw ApiException(ErrorKind.Parse)
            val source = body.source()
            if (source.request(limit + 1)) throw ApiException(ErrorKind.Parse)
            source.readByteArray().also {
                if (it.size < 3 || it[0] != 0xff.toByte() || it[1] != 0xd8.toByte()) throw ApiException(ErrorKind.Parse)
            }
        }
    }

    override suspend fun submit(owner: RequestSession, input: DonationInput, info: DonationInfo): DonationHistory {
        input.validation(info)?.let { throw ApiException(ErrorKind.Unknown, 400, it) }
        val response = api.decode<DonationResponse>(api.request("POST", "donation", body = buildJsonObject {
            put("amount", JsonPrimitive(input.amount.trim().toBigDecimal()))
            put("txnId", input.transaction.trim())
            put("note", input.note)
        }, authenticated = true, expectedSession = owner, projectOnly = true, retryConnection = false))
        if (!response.ok) throw ApiException(ErrorKind.Parse)
        return response.mine
    }
}

fun donationMoney(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
