package com.navalrishi.busnotifier.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeyStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bus_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(): String? = prefs.getString(K_API_KEY, null)?.takeIf { it.isNotBlank() }
    fun setApiKey(value: String) { prefs.edit().putString(K_API_KEY, value.trim()).apply() }
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    private companion object { const val K_API_KEY = "at_api_key" }
}
