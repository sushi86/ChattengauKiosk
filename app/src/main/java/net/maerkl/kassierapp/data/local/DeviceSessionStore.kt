package net.maerkl.kassierapp.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface DeviceSessionStore {
    fun getVereinId(): String?
    fun getGeraetId(): String?
    fun save(vereinId: String, geraetId: String)
    fun clear()
}

class EncryptedDeviceSessionStore(context: Context) : DeviceSessionStore {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "device_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getVereinId(): String? = prefs.getString(KEY_VEREIN_ID, null)
    override fun getGeraetId(): String? = prefs.getString(KEY_GERAET_ID, null)

    override fun save(vereinId: String, geraetId: String) {
        prefs.edit()
            .putString(KEY_VEREIN_ID, vereinId)
            .putString(KEY_GERAET_ID, geraetId)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_VEREIN_ID = "verein_id"
        private const val KEY_GERAET_ID = "geraet_id"
    }
}
