package net.maerkl.kassierapp

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.sumup.reader.sdk.api.SumUpState
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.preferences.SettingsDataStore

class KassierApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    override fun onCreate() {
        super.onCreate()
        SumUpState.init(this)
        setupKioskMode()
    }

    private fun setupKioskMode() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, KioskAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(packageName)) {
            // Erlaube dieser App, Lock Task Mode zu nutzen
            dpm.setLockTaskPackages(adminComponent, arrayOf(
                packageName,
                "com.android.settings"  // Erlaubt Zugriff auf Android-Systemeinstellungen im Kiosk-Modus
            ))
        }
    }
}
