package com.domedav.mavjegy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class Purchase(
    val id: String,
    val validFrom: String?,
    val validTo: String?,
    val startStation: String?,
    val endStation: String?,
    val status: String,
    val takenOver: Boolean,
    val amount: Double,
    val currency: String
)

data class TicketData(
    val serializedTicketData: String?,
    val jegySorszam: String?
)

data class TicketDetails(
    val ticketData: TicketData?
)

class MavApi(private val tokenStore: TokenStore) {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrl = "https://jegy-a.mav.hu/IK_API_PROD/api/"
    private val jsonBody = "application/json; charset=utf-8".toMediaType()

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("UserEmail", email)
                put("Password", password)
            }.toString().toRequestBody(jsonBody)

            val response = client.newCall(
                Request.Builder()
                    .url(baseUrl + "ProfileApi/GetUserToken")
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()
            ).execute()

            response.use {
                if (!it.isSuccessful) error("GetUserToken failed: HTTP ${it.code}")
                val root = json.parseToJsonElement(it.body!!.string()).jsonObject
                val userData = root["userData"]?.jsonObject
                    ?: error("GetUserToken: missing userData")
                val token = userData["userTokenXml"]?.jsonPrimitive?.content
                    ?: error("GetUserToken: missing userTokenXml")
                tokenStore.setToken(token)
                tokenStore.setCredentials(email, password)
            }
        }
    }

    suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        if (!tokenStore.hasToken()) {
            return@withContext reloginIfPossible()
        }
        runCatching {
            val body = "{}".toRequestBody(jsonBody)
            val request = Request.Builder()
                .url(baseUrl + "ProfileApi/RefreshUserToken")
                .header("User-Agent", USER_AGENT)
                .header("UserTokenXml", tokenStore.getToken()!!)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val root = json.parseToJsonElement(response.body!!.string()).jsonObject
                val token = root["userData"]?.jsonObject
                    ?.get("userTokenXml")?.jsonPrimitive?.content
                if (!token.isNullOrBlank()) {
                    tokenStore.setToken(token)
                    true
                } else {
                    false
                }
            }
        }.getOrElse { false }
    }

    suspend fun getPurchases(): List<Purchase> = withContext(Dispatchers.IO) {
        val email = tokenStore.getEmail() ?: error("No stored email")
        val body = buildJsonObject { put("userEmail", email) }
            .toString().toRequestBody(jsonBody)

        fun doCall(token: String) = client.newCall(
            Request.Builder()
                .url(baseUrl + "ProfileApi/GetPreviousPurchases")
                .header("User-Agent", USER_AGENT)
                .header("UserTokenXml", token)
                .post(body)
                .build()
        ).execute()

        var response = doCall(tokenStore.getToken() ?: error("No stored token"))
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (!ensureSession()) error("Session expired and re-login failed")
            response = doCall(tokenStore.getToken()!!)
        }

        response.use {
            if (!it.isSuccessful) error("GetPreviousPurchases failed: HTTP ${it.code}")
            val root = json.parseToJsonElement(it.body!!.string()).jsonObject
            val arr = root["previousPurchases"]?.jsonArray
            arr?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                Purchase(
                    id = o.str("id") ?: return@mapNotNull null,
                    validFrom = o.str("validFrom"),
                    validTo = o.str("validTo"),
                    startStation = o.str("startStation"),
                    endStation = o.str("endStation"),
                    status = o.str("status") ?: "",
                    takenOver = o.bool("takenOver"),
                    amount = o.priceAmount(),
                    currency = o.currencyKey()
                )
            } ?: emptyList()
        }
    }

    suspend fun getTicketDetails(id: String): TicketDetails = withContext(Dispatchers.IO) {
        val email = tokenStore.getEmail() ?: error("No stored email")
        val payload = buildJsonObject {
            put("Id", id)
            put("UserEmail", email)
        }.toString().toRequestBody(jsonBody)

        fun doCall(token: String) = client.newCall(
            Request.Builder()
                .url(baseUrl + "ProfileApi/GetPreviousPurchaseDetails")
                .header("User-Agent", USER_AGENT)
                .header("UserTokenXml", token)
                .post(payload)
                .build()
        ).execute()

        var response = doCall(tokenStore.getToken() ?: error("No stored token"))
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (!ensureSession()) error("Session expired and re-login failed")
            response = doCall(tokenStore.getToken()!!)
        }

        response.use {
            if (!it.isSuccessful) error("GetPreviousPurchaseDetails failed: HTTP ${it.code}")
            val root = json.parseToJsonElement(it.body!!.string()).jsonObject
            val details = root["previousPurchaseDetails"]?.jsonObject
            val tickets = details?.get("ticketDatas")?.jsonArray
            val first = tickets?.firstOrNull() as? JsonObject
            TicketDetails(
                ticketData = first?.let { o ->
                    TicketData(
                        serializedTicketData = o.str("serializedTicketData"),
                        jegySorszam = o.str("jegySorszam")
                    )
                }
            )
        }
    }

    private suspend fun reloginIfPossible(): Boolean {
        if (!tokenStore.hasCredentials()) return false
        return login(tokenStore.getEmail()!!, tokenStore.getPassword()!!).isSuccess
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { !it.content.isBlank() }?.content

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"

    private fun JsonObject.priceAmount(): Double {
        val priceObj = this["price"] as? JsonObject ?: return 0.0
        val amt = priceObj["amount"] as? kotlinx.serialization.json.JsonPrimitive ?: return 0.0
        return amt.content.toDoubleOrNull() ?: 0.0
    }

    private fun JsonObject.currencyKey(): String {
        val priceObj = this["price"] as? JsonObject ?: return ""
        val cur = priceObj["currency"] as? JsonObject ?: return ""
        return (cur["key"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
