package net.maerkl.kassierapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val AFFILIATE_KEY = stringPreferencesKey("affiliate_key")
        val OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        val PIN = stringPreferencesKey("pin")
    }

    val affiliateKey: Flow<String> = context.dataStore.data.map { it[AFFILIATE_KEY] ?: "" }
    val oauthToken: Flow<String> = context.dataStore.data.map { it[OAUTH_TOKEN] ?: "" }
    val pin: Flow<String> = context.dataStore.data.map { it[PIN] ?: "0000" }

    suspend fun saveAffiliateKey(key: String) {
        context.dataStore.edit { it[AFFILIATE_KEY] = key }
    }

    suspend fun saveOauthToken(token: String) {
        context.dataStore.edit { it[OAUTH_TOKEN] = token }
    }

    suspend fun savePin(pin: String) {
        context.dataStore.edit { it[PIN] = pin }
    }
}
