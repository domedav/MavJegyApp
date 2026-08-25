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
    val currency: String,
    val name: String? = null,
    /** Bérletigazolvány azonosító (NevesitesAzonosito) – pl. 1018935324 */
    val passHolderId: String? = null
)

/**
 * Bérlet-detektálás: nincs vonaladat (startStation null) VAGY a név bérletre utal
 * (pl. Országbérlet, Diákbérlet, Budapest–Szeged bérlet).
 */
fun Purchase.isPassTicket(): Boolean =
    startStation == null || name?.contains("bérlet", ignoreCase = true) == true

data class TicketData(
    val serializedTicketData: String?,
    val jegySorszam: String?,
    val bizonylatTechnikaiAzonosito: String? = null
)

data class TicketDetails(
    val ticketData: TicketData?,
    val ajanlatNev: String? = null,
    val ervenyessegKezdete: String? = null,
    val ervenyessegVege: String? = null
)

data class PassOwnerData(
    val fullName: String?,
    val birthDate: String?,
    val photoBase64: String?,
    val azonosito: String? = null
)

/** A szerver jegyképe (fallback nézethez) + a képből dekódolt hivatalos vonalkód-tartalom */
data class ServerJegyképResult(
    val imageBytes: ByteArray?,
    val barcodeText: String?,
    val fromCache: Boolean = false,
    val error: String? = null
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
    private val vimBaseUrl = "https://vim.mav-start.hu/VIM/PR/20251120/MobileServiceS.svc/rest/"
    private val jsonBody = "application/json; charset=utf-8".toMediaType()

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Demó belépés – teljesen offline dummy adatokkal
        if (DemoData.matches(email, password)) {
            tokenStore.setDemo(true)
            tokenStore.setCredentials(DemoData.DEMO_EMAIL, DemoData.DEMO_PASSWORD)
            return@withContext Result.success(Unit)
        }
        tokenStore.setDemo(false)
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
                // VIM (GetJegykep / BerletTok*) hívásokhoz: felhasználóazonosító, ha adja a szerver
                val userId = listOf("felhasznaloAzonosito", "FelhasznaloAzonosito", "regisztraciosAzonosito")
                    .firstNotNullOfOrNull { k -> (userData[k] as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?.takeIf { it.isNotBlank() }
                if (userId != null) tokenStore.setUserId(userId)
                tokenStore.setToken(token)
                tokenStore.setCredentials(email, password)
            }
        }
    }

    suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) return@withContext true
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
        if (tokenStore.isDemo()) return@withContext DemoData.purchases()
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
            (arr?.mapNotNull { el ->
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
                    currency = o.currencyKey(),
                    name = listOf("name", "Name", "Nev", "nev", "ajanlatNev", "title")
                        .firstNotNullOfOrNull { k -> o.str(k) },
                    passHolderId = o.str("passHolderId")
                )
            } ?: emptyList()).distinctBy { it.id }
        }
    }

    suspend fun getTicketDetails(id: String): TicketDetails = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) return@withContext DemoData.ticketDetails(id)
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
            // ajánlatnév / érvényesség a szolgáltatás-ajánlatokból
            val offer = first?.get("szolgaltatasAjanlatok")?.let { el ->
                (el as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
            }
            TicketDetails(
                ticketData = first?.let { o ->
                    TicketData(
                        serializedTicketData = o.str("serializedTicketData"),
                        jegySorszam = o.str("jegySorszam"),
                        bizonylatTechnikaiAzonosito = o.str("bizonylatTechnikaiAzonosito")
                    )
                },
                ajanlatNev = offer?.str("ajanlatNev"),
                ervenyessegKezdete = offer?.str("ervenyessegKezdete"),
                ervenyessegVege = offer?.str("ervenyessegVege")
            )
        }
    }

    /**
     * Bérletes utas adatai (tulajdonos) – ProfileApi/GetUserHPTDatas.
     * Az eredeti app BerletesUtasAdatokVO mezőit követi:
     * teljesNev, szuletesiDatum, berletKepString / eszigIgazolvanyszamHash stb.
     */
    suspend fun getPassOwnerData(context: android.content.Context): PassOwnerData? = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) return@withContext DemoData.passOwner()
        val email = tokenStore.getEmail() ?: return@withContext null
        val body = buildJsonObject { put("userEmail", email) }
            .toString().toRequestBody(jsonBody)

        fun doCall(token: String) = client.newCall(
            Request.Builder()
                .url(baseUrl + "ProfileApi/GetUserHPTDatas")
                .header("User-Agent", USER_AGENT)
                .header("UserTokenXml", token)
                .post(body)
                .build()
        ).execute()

        var response = doCall(tokenStore.getToken() ?: return@withContext null)
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (!ensureSession()) return@withContext null
            response = doCall(tokenStore.getToken()!!)
        }

        val fresh: PassOwnerData? = try {
            response.use { r ->
                if (!r.isSuccessful) return@use null
                val root = json.parseToJsonElement(r.body!!.string())
                findPassOwner(root)
            }
        } catch (_: Exception) {
            null
        }
        // Agresszív offline cache: frissít, ha tud; hálózatként elérhetetlen esetén cache-ből szolgál
        if (fresh != null) {
            OfflineStore.savePassOwner(context, "global", fresh.fullName, fresh.birthDate, fresh.photoBase64, fresh.azonosito)
            fresh
        } else {
            OfflineStore.loadPassOwner(context, "global")?.let {
                PassOwnerData(it.fullName, it.birthDate, it.photoBase64, it.azonosito)
            }
        }
    }

    private fun findPassOwner(el: kotlinx.serialization.json.JsonElement): PassOwnerData? {
        when (el) {
            is JsonObject -> {
                var fullName: String? = null
                var birthDate: String? = null
                var photo: String? = null
                var azonosito: String? = null
                el.forEach { (key, v) ->
                    val prim = v as? kotlinx.serialization.json.JsonPrimitive
                    val s = prim?.content?.takeIf { it.isNotBlank() && it != "null" }
                    when {
                        s != null && key.equals("teljesNev", true) -> fullName = s
                        s != null && fullName == null && (key.equals("Nev", true) || key.equals("FullName", true)) -> fullName = s
                        s != null && key.equals("szuletesiDatum", true) -> birthDate = s
                        s != null && photo == null && (key.equals("Fenykep", true) || key.equals("berletKepString", true)) -> photo = s
                        s != null && azonosito == null && (
                            key.equals("NevesitesAzonosito", true) ||
                                key.equals("berletIgazolvanyazonosito", true) ||
                                key.equals("eszigIgazolvanyszam", true)
                            ) -> azonosito = s
                    }
                }
                if (fullName != null || birthDate != null || photo != null || azonosito != null) {
                    return PassOwnerData(fullName, birthDate, photo, azonosito)
                }
                el.values.firstNotNullOfOrNull { findPassOwner(it) }?.let { return it }
                return null
            }
            is kotlinx.serialization.json.JsonArray ->
                el.firstNotNullOfOrNull { findPassOwner(it) }?.let { return it }
            else -> {}
        }
        return null
    }


    /** Uzenetek[] (H=hiba, M=üzenet, R=rendszerhiba) szövegek összefűzése */
    private fun extractServerMessages(el: kotlinx.serialization.json.JsonElement): String? {
        val texts = mutableListOf<String>()
        fun walk(e: kotlinx.serialization.json.JsonElement) {
            when (e) {
                is JsonObject -> {
                    val szoveg = (e["Szoveg"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (!szoveg.isNullOrBlank()) texts += szoveg
                    e.values.forEach { walk(it) }
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach { walk(it) }
                else -> {}
            }
        }
        walk(el)
        return texts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /** Rekurzív kulcskeresés a JSON fában (robusztus válaszparse). */
    private fun findStringKey(el: kotlinx.serialization.json.JsonElement, key: String): String? {
        when (el) {
            is JsonObject -> {
                (el[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { p ->
                    if (p.content.isNotBlank() && p.content != "null") return p.content
                }
                el.values.firstNotNullOfOrNull { findStringKey(it, key) }?.let { return it }
            }
            is kotlinx.serialization.json.JsonArray ->
                el.firstNotNullOfOrNull { findStringKey(it, key) }?.let { return it }
            else -> {}
        }
        return null
    }

    /**
     * Utastípus / kedvezmény kód -> emberi név térkép a GetAlapadatok válaszból
     * (PassengerTypeVO: Kod->Nev, DiscountVO: Azonosito->Nev). Hiba esetén üres térkép.
     */
    suspend fun getTypeNames(context: android.content.Context): Map<String, String> = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) return@withContext DemoData.typeNames()
        if (!tokenStore.hasToken()) return@withContext OfflineStore.loadTypeNames(context) ?: emptyMap()
        val body = "{}".toRequestBody(jsonBody)
        val paths = listOf("ProfileApi/GetAlapadatok", "GetAlapadatok", "OfferRequestApi/GetAlapadatok")
        for (path in paths) {
            val result: Map<String, String>? = try {
                client.newCall(
                    Request.Builder()
                        .url(baseUrl + path)
                        .header("User-Agent", USER_AGENT)
                        .header("UserTokenXml", tokenStore.getToken()!!)
                        .post(body)
                        .build()
                ).execute().use { r ->
                    if (!r.isSuccessful) null
                    else collectTypeNames(json.parseToJsonElement(r.body!!.string()), mutableMapOf())
                }
            } catch (_: Exception) {
                null
            }
            if (!result.isNullOrEmpty()) {
                OfflineStore.saveTypeNames(context, result)
                return@withContext result
            }
        }
        // offline fallback
        OfflineStore.loadTypeNames(context) ?: emptyMap()
    }

    private fun collectTypeNames(
        el: kotlinx.serialization.json.JsonElement,
        out: MutableMap<String, String>
    ): Map<String, String> {
        when (el) {
            is JsonObject -> {
                fun s(k: String) = (el[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val kod = s("Kod") ?: s("kod") ?: s("Azonosito")
                val nev = s("Nev") ?: s("nev")
                if (kod != null && nev != null) out[kod] = nev
                el.values.forEach { collectTypeNames(it, out) }
            }
            is kotlinx.serialization.json.JsonArray -> el.forEach { collectTypeNames(it, out) }
            else -> {}
        }
        return out
    }

    /**
     * Regisztráció – POST {VIM}/Regisztracio (az eredeti app RequestGetRegistration-ja).
     * Body: {Nyelv, RegisztraciosAdat:{EmailCim, Jelszo, VezetekNev, KeresztNev, SzuletesiDatum}, UAID}
     * (Szolgaltato/Token/OneTimeCode az eredeti appban is üres -> Gson kihagyja.)
     */
    suspend fun register(
        email: String,
        lastName: String,
        firstName: String,
        birthDateIso: String,
        password: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) {
            return@withContext Result.success(null)
        }
        if (!tokenStore.hasUaid()) tokenStore.setUaid(java.util.UUID.randomUUID().toString())

        val birthEpochSec = try {
            java.time.LocalDate.parse(birthDateIso.take(10))
                .atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond().toString()
        } catch (_: Exception) {
            null
        }

        val body = buildJsonObject {
            put("Nyelv", "hu")
            put("RegisztraciosAdat", buildJsonObject {
                put("EmailCim", email)
                put("Jelszo", password)
                put("VezetekNev", lastName)
                put("KeresztNev", firstName)
                if (birthEpochSec != null) put("SzuletesiDatum", birthEpochSec)
            })
            put("UAID", tokenStore.getUaid())
        }.toString().toRequestBody(jsonBody)

        runCatching {
            client.newCall(
                Request.Builder()
                    .url(vimBaseUrl + "Regisztracio")
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()
            ).execute().use { r ->
                val root = json.parseToJsonElement(r.body!!.string())
                val ok = findStringKey(root, "Valasz") == "true"
                if (!ok) error(extractServerMessages(root) ?: "Sikertelen regisztráció (HTTP ${r.code})")
                null // siker: a szerver emailben küldi a megerősítést
            }
        }
    }

    /** Elfelejtett jelszó – POST {VIM}/GetUjJelszo, a szerver új jelszót küld emailben. */
    suspend fun forgotPassword(email: String): Result<String?> = withContext(Dispatchers.IO) {
        if (tokenStore.isDemo()) {
            return@withContext Result.success("Demó mód – valós emailt nem küldünk")
        }
        if (!tokenStore.hasUaid()) tokenStore.setUaid(java.util.UUID.randomUUID().toString())

        val body = buildJsonObject {
            put("FelhasznaloAzonosito", email)
            put("Nyelv", "hu")
            put("UAID", tokenStore.getUaid())
        }.toString().toRequestBody(jsonBody)

        runCatching {
            client.newCall(
                Request.Builder()
                    .url(vimBaseUrl + "GetUjJelszo")
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()
            ).execute().use { r ->
                val root = json.parseToJsonElement(r.body!!.string())
                val ok = findStringKey(root, "Valasz") == "true"
                if (!ok) error(extractServerMessages(root) ?: "Sikertelen kérés (HTTP ${r.code})")
                null
            }
        }
    }

    /**
     * VIM (MobileServiceS) bejelentkezés – a GetJegykep EHHEZ a tokenhez kell,
     * NEM az IK SAML userTokenXml-hez (Bejelentkezes -> LoginResponseVO.Token).
     * FIGYELEM: magyar IP-ről működik; külföldi IP-ről a MÁV WAF blokkolja
     * a jelszavas kéréseket (hozzáférési szabályzat).
     */
    private suspend fun ensureVimSession(): Boolean = withContext(Dispatchers.IO) {
        val expiry = tokenStore.getVimTokenExpiry()
        if (!tokenStore.getVimToken().isNullOrBlank() &&
            expiry > System.currentTimeMillis() + 60_000L
        ) return@withContext true

        val email = tokenStore.getEmail() ?: return@withContext false
        val password = tokenStore.getPassword() ?: return@withContext false
        if (!tokenStore.hasUaid()) tokenStore.setUaid(java.util.UUID.randomUUID().toString())

        val body = buildJsonObject {
            put("FelhasznaloAzonosito", email)
            put("Jelszo", password)
            put("Nyelv", "hu")
            put("UAID", tokenStore.getUaid())
        }.toString().toRequestBody(jsonBody)

        try {
            client.newCall(
                Request.Builder()
                    .url(vimBaseUrl + "Bejelentkezes")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "gzip")
                    .post(body)
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) return@withContext false
                val ct = r.header("Content-Type") ?: ""
                if (!ct.contains("json")) return@withContext false // WAF HTML blokk
                val root = json.parseToJsonElement(r.body!!.string())
                val token = findStringKey(root, "Token") ?: return@withContext false
                tokenStore.setVimToken(token)
                tokenStore.setVimTokenExpiry(parseVimExpiry(findStringKey(root, "ErvenyessegVege")))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseVimExpiry(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        Regex("/Date\\((-?\\d+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        raw.toLongOrNull()?.let { return if (it > 99_999_999_999L) it else it * 1000L }
        return try {
            java.time.ZonedDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * A SZERVER-OLDALI jegykép: első lekérés után MENTJÜK (hash-dedup + kompresszió),
     * és a KÉPEN LÉVŐ VONALKÓDOT dekódoljuk – ez az egyetlen hivatalos, scannelhető
     * bérlet-kód, amit lokálisan Aztec-ként is megjelenítünk.
     */
    suspend fun getServerJegyKep(
        purchaseId: String,
        bizonylatTechnikaiAzonosito: String?,
        context: android.content.Context
    ): ServerJegyképResult = withContext(Dispatchers.IO) {
        // 1. Disk cache: kép + (ha van) már dekódolt kódszöveg
        val cachedImage = OfflineStore.loadServerJegyKep(context, purchaseId)
        val cachedText = OfflineStore.loadServerBarcode(context, purchaseId)
        if (cachedImage != null) {
            var text = cachedText
            if (text.isNullOrBlank()) {
                // képből utólagos dekódolás
                val bmp = android.graphics.BitmapFactory.decodeByteArray(cachedImage, 0, cachedImage.size)
                text = bmp?.let { com.domedav.mavjegy.util.BarcodeImageDecoder.decode(it) }
                if (!text.isNullOrBlank()) OfflineStore.saveServerBarcode(context, purchaseId, text)
            }
            return@withContext ServerJegyképResult(cachedImage, text, fromCache = true)
        }

        if (bizonylatTechnikaiAzonosito.isNullOrBlank()) {
            return@withContext ServerJegyképResult(null, cachedText, error = "Nincs bizonylat-azonosító")
        }
        if (tokenStore.isDemo()) {
            return@withContext ServerJegyképResult(null, cachedText, error = "Demó módban nincs szerver jegykép")
        }
        if (!ensureVimSession()) {
            return@withContext ServerJegyképResult(
                null, cachedText,
                error = "VIM bejelentkezés sikertelen (magyar IP szükséges)"
            )
        }

        val body = buildJsonObject {
            put("BizonylatAzonosito", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(bizonylatTechnikaiAzonosito))
            })
            put("FelhasznaloAzonosito", tokenStore.getUserId() ?: tokenStore.getEmail() ?: "")
            put("Nyelv", "hu")
            put("Token", tokenStore.getVimToken())
            put("UAID", tokenStore.getUaid())
        }.toString().toRequestBody(jsonBody)

        try {
            client.newCall(
                Request.Builder()
                    .url(vimBaseUrl + "GetJegykep")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "gzip")
                    .post(body)
                    .build()
            ).execute().use { r ->
                val ct = r.header("Content-Type") ?: ""
                if (!r.isSuccessful || !ct.contains("json")) {
                    return@withContext ServerJegyképResult(
                        null, cachedText,
                        error = "GetJegykep blokkolva/hibás (HTTP ${r.code})"
                    )
                }
                val root = json.parseToJsonElement(r.body!!.string())
                val b64 = findStringKey(root, "Jegykep")
                if (b64.isNullOrBlank()) {
                    return@withContext ServerJegyképResult(
                        null, cachedText,
                        error = extractServerMessages(root) ?: "A szerver nem adott vissza jegyképet"
                    )
                }
                val bytes = java.util.Base64.getDecoder().decode(b64)
                OfflineStore.saveServerJegyKep(context, purchaseId, bytes)

                var text = OfflineStore.loadServerBarcode(context, purchaseId)
                if (text.isNullOrBlank()) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    text = bmp?.let { com.domedav.mavjegy.util.BarcodeImageDecoder.decode(it) }
                    if (!text.isNullOrBlank()) OfflineStore.saveServerBarcode(context, purchaseId, text)
                }
                ServerJegyképResult(bytes, text)
            }
        } catch (e: Exception) {
            ServerJegyképResult(null, cachedText, error = e.message ?: "Hálózati hiba")
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
