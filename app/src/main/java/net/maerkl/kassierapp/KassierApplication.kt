package net.maerkl.kassierapp

import android.app.Application
import com.sumup.reader.sdk.api.SumUpState
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.preferences.SettingsDataStore

class KassierApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    override fun onCreate() {
        super.onCreate()
        SumUpState.init(this)
    }
}
