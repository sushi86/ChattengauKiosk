package net.maerkl.kassierapp.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SelectedSortimentStore {
    val selectedSortimentId: StateFlow<String?>
    fun set(id: String?)
}

class EncryptedSelectedSortimentStore(context: Context) : SelectedSortimentStore {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "selected_sortiment",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(prefs.getString(KEY, null))
    override val selectedSortimentId: StateFlow<String?> = _state.asStateFlow()

    override fun set(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY) else putString(KEY, id)
        }.apply()
        _state.value = id
    }

    companion object {
        private const val KEY = "selectedSortimentId"
    }
}
