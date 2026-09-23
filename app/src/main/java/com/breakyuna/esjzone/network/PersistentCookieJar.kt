package com.breakyuna.esjzone.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale

/**
 * A small persistent CookieJar for the server-rendered ESJ session.
 *
 * The browser keeps the complete Set-Cookie state.  Keeping the same state here is
 * important because the site can rotate session cookies after a successful request.
 */
internal class PersistentCookieJar(context: Context) : CookieJar {

    /**
     * Non-secret cache metadata remains in ordinary preferences. Session cookies are
     * stored separately with an Android Keystore-backed AES key. There is deliberately
     * no plaintext fallback when the secure store cannot be opened.
     */
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val securePreferences: SharedPreferences? = createSecurePreferences(context)
    private val gson = Gson()
    private val lock = Any()
    private val cookies = mutableListOf<StoredCookie>()
    private var epoch = 0L

    fun sessionEpoch(): Long = synchronized(lock) { epoch }

    init {
        migrateLegacyCookies()
        cookies += loadCookies()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = loadForRequest(url, expectedEpoch = null)

    fun loadForRequest(url: HttpUrl, expectedEpoch: Long?): List<Cookie> {
        synchronized(lock) {
            if (expectedEpoch != null && expectedEpoch != epoch) return emptyList()
            val changed = removeExpiredCookies()
            if (changed) persistLocked()
            return cookies.mapNotNull { it.toCookie() }.filter { it.matches(url) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
        saveFromResponse(url, cookies, expectedEpoch = null)

    fun saveFromResponse(url: HttpUrl, responseCookies: List<Cookie>, expectedEpoch: Long?) {
        if (responseCookies.isEmpty()) return
        synchronized(lock) {
            if (expectedEpoch != null && expectedEpoch != epoch) return
            removeExpiredCookies()
            for (cookie in responseCookies) {
                val index = cookies.indexOfFirst { it.sameIdentity(cookie) }
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    // A WAF/interstitial can emit a broad session-cookie deletion even
                    // though the HTML request itself was never accepted. Do not let an
                    // arbitrary page response destroy ews_*; explicit clearSession() and
                    // the login flow remain the authoritative deletion/rotation paths.
                    if (cookie.name == "ews_key" || cookie.name == "ews_token") continue
                    if (index >= 0) cookies.removeAt(index)
                } else {
                    val stored = StoredCookie.from(cookie)
                    if (index >= 0) {
                        cookies[index] = stored
                    } else {
                        cookies += stored
                    }
                }
            }
            val activeIdentity = securePreferences?.getString(activeAccountScopeKey(url.host), null)
            val rotatedKey = responseCookies.firstOrNull {
                it.name == "ews_key" && it.expiresAt > System.currentTimeMillis()
            }?.value
            if (!activeIdentity.isNullOrBlank() && !rotatedKey.isNullOrBlank()) {
                securePreferences?.edit()
                    ?.putString(accountKeyMappingKey(url.host, rotatedKey), activeIdentity)
                    ?.apply()
            }
            persistLocked()
        }
    }

    fun authorizationFor(host: String): Authorization? {
        val url = runCatching {
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .build()
        }.getOrNull() ?: return null
        val matching = loadForRequest(url)
        val key = matching.firstOrNull { it.name == "ews_key" }?.value
        val token = matching.firstOrNull { it.name == "ews_token" }?.value
        return if (!key.isNullOrBlank() && !token.isNullOrBlank()) {
            Authorization(key, token, host)
        } else {
            null
        }
    }

    /**
     * Returns an opaque, host-scoped cache identity that survives cookie rotation.
     * Session cookies are deliberately excluded: ESJ can rotate them after any
     * response, which previously made every cached page unreachable after restart.
     */
    fun cacheScopeFor(host: String): String = synchronized(lock) {
        val key = cacheScopeKey(host)
        preferences.getString(key, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
            .toString()
            .also { preferences.edit().putString(key, it).commit() }
    }

    /** Bookshelf identity is independent of the disposable page-cache namespace. */
    fun accountScopeFor(host: String, ewsKey: String): String = synchronized(lock) {
        securePreferences?.getString(accountKeyMappingKey(host, ewsKey), null)
            ?.takeIf(String::isNotBlank)?.let { return@synchronized it }
        val currentKey = authorizationFor(host)?.ewsKey
        if (currentKey == ewsKey) {
            securePreferences?.getString(activeAccountScopeKey(host), null)
                ?.takeIf(String::isNotBlank) ?: cacheScopeFor(host)
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(ewsKey.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            "stale:$digest"
        }
    }

    /** Reuse one opaque identity when the same account explicitly logs in again. */
    fun activateAccountScope(host: String, email: String, newEwsKey: String) = synchronized(lock) {
        val secure = securePreferences ?: return@synchronized
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank()) return@synchronized
        val identityDigest = MessageDigest.getInstance("SHA-256")
            .digest("${normalizedHost(host)}:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val mappingKey = ACCOUNT_SCOPE_PREFIX + identityDigest
        val existingIdentity = secure.getString(mappingKey, null)?.takeIf(String::isNotBlank)
        val previousAuthorization = authorizationFor(host)
        val legacyCacheScope = if (existingIdentity == null &&
            secure.getString(activeAccountScopeKey(host), null).isNullOrBlank() &&
            previousAuthorization?.ewsKey != newEwsKey
        ) preferences.getString(cacheScopeKey(host), null)?.takeIf(String::isNotBlank) else null
        val identity = existingIdentity ?: if (previousAuthorization?.ewsKey == newEwsKey) {
            accountScopeFor(host, newEwsKey)
        } else {
            UUID.randomUUID().toString()
        }
        val editor = secure.edit()
            .putString(mappingKey, identity)
            .putString(activeAccountScopeKey(host), identity)
            .putString(accountKeyMappingKey(host, newEwsKey), identity)
        if (legacyCacheScope != null) {
            editor.putString(pendingLegacyScopeKey(host, identity), "account:$host:$legacyCacheScope")
        }
        editor.commit()
    }

    fun pendingLegacyScopeFor(host: String, ewsKey: String): String? = synchronized(lock) {
        val identity = accountScopeFor(host, ewsKey)
        securePreferences?.getString(pendingLegacyScopeKey(host, identity), null)
            ?.takeIf { it.startsWith("account:$host:") }
    }

    fun clearPendingLegacyScope(host: String, ewsKey: String) = synchronized(lock) {
        val identity = accountScopeFor(host, ewsKey)
        securePreferences?.edit()?.remove(pendingLegacyScopeKey(host, identity))?.commit()
        Unit
    }

    /** Starts a new cache namespace when an explicit login changes account state. */
    fun rotateCacheScope(host: String) = synchronized(lock) {
        epoch++
        preferences.edit()
            .putString(cacheScopeKey(host), UUID.randomUUID().toString())
            .commit()
        Unit
    }

    fun wasVerifiedRecently(authorization: Authorization, maxAgeMillis: Long): Boolean = synchronized(lock) {
        val verifiedAt = preferences.getLong(verificationKey(authorization), 0L)
        val age = System.currentTimeMillis() - verifiedAt
        verifiedAt > 0L && age in 0..maxAgeMillis
    }

    fun markVerified(authorization: Authorization) = synchronized(lock) {
        preferences.edit()
            .putLong(verificationKey(authorization), System.currentTimeMillis())
            .apply()
    }

    /** Imports the two-cookie format written by older app versions. */
    fun importLegacyAuthorization(host: String, authorization: Authorization): Boolean {
        if (!authorization.hasCredentials()) return false
        val migrationHost = normalizedHost(host)
        val url = runCatching {
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .build()
        }.getOrNull() ?: return false
        synchronized(lock) {
            val migratedHosts = preferences.getStringSet(LEGACY_MIGRATED_HOSTS, emptySet()).orEmpty()
            if (migrationHost in migratedHosts) return false
            saveFromResponse(
                url,
                listOf(
                    legacyCookie(host, "ews_key", authorization.ewsKey),
                    legacyCookie(host, "ews_token", authorization.ewsToken)
                )
            )
            preferences.edit()
                .putStringSet(LEGACY_MIGRATED_HOSTS, migratedHosts + migrationHost)
                .apply()
            return true
        }
    }

    fun clear(host: String? = null) {
        synchronized(lock) {
            epoch++
            if (host.isNullOrBlank()) {
                securePreferences?.let { secure ->
                    val editor = secure.edit()
                    secure.all.keys.filter { it.startsWith(ACTIVE_ACCOUNT_SCOPE_PREFIX) }
                        .forEach(editor::remove)
                    editor.commit()
                }
                cookies.clear()
                val editor = preferences.edit()
                preferences.all.keys
                    .filter {
                        it.startsWith(CACHE_SCOPE_PREFIX) ||
                            it.startsWith(VERIFIED_AT_PREFIX)
                    }
                    .forEach(editor::remove)
                editor.commit()
            } else {
                securePreferences?.edit()?.remove(activeAccountScopeKey(host))?.commit()
                val normalizedHost = host.trim().lowercase().removePrefix("www.")
                cookies.removeAll {
                    domainMatchesHost(it.domain, normalizedHost) ||
                        domainMatchesHost(it.domain, "www.$normalizedHost")
                }
                val editor = preferences.edit()
                    .remove(cacheScopeKey(host))
                    // Remove both the current session-scoped keys and the
                    // pre-session-scoped key used by older versions.
                    .remove(legacyVerificationKey(host))
                preferences.all.keys
                    .filter { it.startsWith(verificationPrefix(host)) }
                    .forEach(editor::remove)
                editor.commit()
            }
            persistLocked()
        }
    }

    private fun legacyCookie(host: String, name: String, value: String): Cookie {
        val normalizedHost = host.trim().lowercase().removePrefix("www.")
        return Cookie.Builder()
            .domain(normalizedHost)
            .path("/")
            .name(name)
            .value(value)
            .secure()
            .build()
    }

    private fun removeExpiredCookies(): Boolean {
        val now = System.currentTimeMillis()
        return cookies.removeAll { it.expiresAt <= now }
    }

    private fun loadCookies(): MutableList<StoredCookie> {
        val json = runCatching { securePreferences?.getString(COOKIES, null) }
            .getOrNull()
            ?: return mutableListOf()
        return parseCookies(json)
    }

    private fun parseCookies(json: String): MutableList<StoredCookie> {
        return try {
            gson.fromJson(json, Array<StoredCookie>::class.java)?.toMutableList()
                ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun persistLocked() {
        runCatching {
            securePreferences?.edit()?.putString(COOKIES, gson.toJson(cookies))?.apply()
        }.onFailure {
            // Keep the current process usable, but never fall back to plaintext storage.
            Log.e(TAG, "Unable to persist the secure session", it)
        }
    }

    /** Migrates the old plaintext cookie JSON once, then removes that copy. */
    private fun migrateLegacyCookies() = synchronized(lock) {
        val legacyJson = preferences.getString(COOKIES, null)
        val secure = securePreferences
        runCatching {
            if (secure != null && !secure.contains(COOKIES) && !legacyJson.isNullOrBlank()) {
                val migrated = parseCookies(legacyJson)
                if (migrated.isNotEmpty()) {
                    secure.edit().putString(COOKIES, gson.toJson(migrated)).commit()
                }
            }
        }.onFailure {
            Log.e(TAG, "Unable to migrate the legacy session securely", it)
        }
        // Never retain a plaintext session copy, including when secure storage was
        // unavailable; failing closed is safer than allowing a future backup to copy it.
        if (legacyJson != null) preferences.edit().remove(COOKIES).commit()
    }

    private fun domainMatchesHost(domain: String, host: String): Boolean {
        val normalizedDomain = domain.lowercase().removePrefix(".")
        return host == normalizedDomain || host.endsWith(".$normalizedDomain")
    }

    private fun normalizedHost(host: String): String =
        host.trim().lowercase().removePrefix("www.")

    private fun cacheScopeKey(host: String): String =
        CACHE_SCOPE_PREFIX + normalizedHost(host)

    private fun activeAccountScopeKey(host: String): String =
        ACTIVE_ACCOUNT_SCOPE_PREFIX + normalizedHost(host)

    private fun accountKeyMappingKey(host: String, ewsKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${normalizedHost(host)}:$ewsKey".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return ACCOUNT_KEY_SCOPE_PREFIX + digest
    }

    private fun pendingLegacyScopeKey(host: String, identity: String): String =
        PENDING_LEGACY_SCOPE_PREFIX + normalizedHost(host) + ":" + identity

    private fun verificationKey(authorization: Authorization): String =
        verificationPrefix(authorization.domain) + sessionDigest(authorization)

    private fun verificationPrefix(host: String): String =
        VERIFIED_AT_PREFIX + normalizedHost(host) + "_"

    private fun legacyVerificationKey(host: String): String =
        VERIFIED_AT_PREFIX + normalizedHost(host)

    private fun sessionDigest(authorization: Authorization): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                "${authorization.ewsKey}:${authorization.ewsToken}"
                    .toByteArray(StandardCharsets.UTF_8)
            )
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) {
        fun toCookie(): Cookie? {
            return runCatching {
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                if (hostOnly) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (expiresAt != Long.MAX_VALUE) builder.expiresAt(expiresAt)
                if (secure || name in SESSION_COOKIE_NAMES) builder.secure()
                if (httpOnly) builder.httpOnly()
                builder.build()
            }.getOrNull()
        }

        fun sameIdentity(cookie: Cookie): Boolean =
            name == cookie.name &&
                domain.equals(cookie.domain, ignoreCase = true) &&
                path == cookie.path

        companion object {
            fun from(cookie: Cookie): StoredCookie = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly
            )
        }
    }

    private companion object {
        const val PREFERENCES = "esj_session"
        const val COOKIES = "cookies"
        const val LEGACY_MIGRATED_HOSTS = "legacy_migrated_hosts"
        const val CACHE_SCOPE_PREFIX = "cache_scope_"
        const val ACCOUNT_SCOPE_PREFIX = "account_identity_"
        const val ACTIVE_ACCOUNT_SCOPE_PREFIX = "active_account_identity_"
        const val ACCOUNT_KEY_SCOPE_PREFIX = "account_key_identity_"
        const val PENDING_LEGACY_SCOPE_PREFIX = "pending_legacy_bookshelf_"
        const val VERIFIED_AT_PREFIX = "verified_at_"
        val SESSION_COOKIE_NAMES = setOf("ews_key", "ews_token")

        private fun createSecurePreferences(context: Context): SharedPreferences? {
            val appContext = context.applicationContext
            fun create(): SharedPreferences {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFERENCES,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }

            return try {
                create()
            } catch (error: Exception) {
                // Keyset files restored from another device cannot be decrypted by this
                // device's Android Keystore. Remove the complete encrypted-preference set
                // once, then recreate it instead of leaving every later launch logged out.
                Log.w(TAG, "Resetting unreadable secure session storage", error)
                appContext.deleteSharedPreferences(SECURE_PREFERENCES)
                appContext.deleteSharedPreferences(KEY_KEYSET_PREFERENCES)
                appContext.deleteSharedPreferences(VALUE_KEYSET_PREFERENCES)
                runCatching { create() }.onFailure {
                    Log.e(TAG, "Secure session storage is unavailable; session persistence disabled", it)
                }.getOrNull()
            }
        }

        const val SECURE_PREFERENCES = "esj_session_secure"
        const val KEY_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val VALUE_KEYSET_PREFERENCES = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        const val TAG = "PersistentCookieJar"
    }
}
