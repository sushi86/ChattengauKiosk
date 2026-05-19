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
        val PIN = stringPreferencesKey("pin")
    }

    val pin: Flow<String> = context.dataStore.data.map { it[PIN] ?: "0000" }

    suspend fun savePin(pin: String) {
        context.dataStore.edit { it[PIN] = pin }
    }
}
