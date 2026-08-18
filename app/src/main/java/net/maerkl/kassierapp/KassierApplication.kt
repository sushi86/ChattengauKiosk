package net.maerkl.kassierapp

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.sumup.reader.sdk.api.SumUpState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.local.EncryptedDeviceSessionStore
import net.maerkl.kassierapp.data.local.EncryptedSelectedSortimentStore
import net.maerkl.kassierapp.data.local.SelectedSortimentStore
import net.maerkl.kassierapp.data.preferences.SettingsDataStore
import net.maerkl.kassierapp.data.remote.AppCheckTokenProvider
import net.maerkl.kassierapp.data.remote.ArtikelRepository
import net.maerkl.kassierapp.data.remote.BackendApi
import net.maerkl.kassierapp.data.remote.SumupTransactionStatusApi
import net.maerkl.kassierapp.data.remote.FirebasePairingService
import net.maerkl.kassierapp.data.remote.SortimentRepository
import net.maerkl.kassierapp.data.remote.TransaktionRepository
import net.maerkl.kassierapp.data.remote.ZahlungsprotokollRepository
import net.maerkl.kassierapp.data.repository.DeviceSessionRepository
import net.maerkl.kassierapp.data.repository.FirebaseAuthSignOut
import net.maerkl.kassierapp.data.repository.FirebaseIdTokenSource
import net.maerkl.kassierapp.data.repository.PairingRepository
import net.maerkl.kassierapp.data.repository.SumupTokenRepository

class KassierApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    private lateinit var httpClient: HttpClient
    lateinit var deviceSessionRepository: DeviceSessionRepository
        private set
    lateinit var pairingRepository: PairingRepository
        private set
    lateinit var sumupTokenRepository: SumupTokenRepository
        private set
    lateinit var sumupTransactionStatusApi: SumupTransactionStatusApi
        private set
    lateinit var transaktionRepository: TransaktionRepository
        private set
    lateinit var zahlungsprotokollRepository: ZahlungsprotokollRepository
        private set
    lateinit var artikelRepository: ArtikelRepository
        private set
    lateinit var sortimentRepository: SortimentRepository
        private set
    lateinit var selectedSortimentStore: SelectedSortimentStore
        private set

    override fun onCreate() {
        super.onCreate()

        val firebaseApp = FirebaseApp.initializeApp(this)!!
        val appCheck = FirebaseAppCheck.getInstance()
        val providerFactory = if (BuildConfig.DEBUG) {
            pinDebugAppCheckSecret(firebaseApp)
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(providerFactory)

        val auth = FirebaseAuth.getInstance()
        val functions = FirebaseFunctions.getInstance(Config.FIREBASE_FUNCTIONS_REGION)

        val appCheckProvider = AppCheckTokenProvider(appCheck)
        // Firestore attaches the App Check token lazily and sends the very first
        // request of a session without one if it isn't cached yet, which our
        // security rules (request.app != null) reject. Warm the cache here,
        // before any Firestore listener (catalog, transactions) can start —
        // covers both the post-pairing flow and cold start while already paired.
        // Fire-and-forget on a background dispatcher: must NOT block onCreate
        // (main thread) on a network call, or a slow/offline network causes
        // an ANR on app launch.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appCheckProvider.getToken()
            } catch (e: Exception) {
                Log.w("KassierApplication", "App Check token pre-fetch failed", e)
            }
        }

        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val backendApi = BackendApi(httpClient, Config.BACKEND_BASE_URL)
        sumupTransactionStatusApi = SumupTransactionStatusApi(httpClient)
        val pairingService = FirebasePairingService(functions)
        val sessionStore = EncryptedDeviceSessionStore(this)

        deviceSessionRepository = DeviceSessionRepository(sessionStore, FirebaseAuthSignOut(auth))
        pairingRepository = PairingRepository(pairingService, auth, deviceSessionRepository)
        sumupTokenRepository = SumupTokenRepository(
            api = backendApi,
            idTokenSource = FirebaseIdTokenSource(auth),
            appCheck = appCheckProvider,
            sessionRepo = deviceSessionRepository
        )
        val firestore = FirebaseFirestore.getInstance()
        transaktionRepository = TransaktionRepository(
            firestore = firestore,
            auth = auth,
            sessionRepo = deviceSessionRepository,
        )
        zahlungsprotokollRepository = ZahlungsprotokollRepository(
            firestore = firestore,
            auth = auth,
            sessionRepo = deviceSessionRepository,
        )
        artikelRepository = ArtikelRepository(firestore)
        sortimentRepository = SortimentRepository(firestore)
        selectedSortimentStore = EncryptedSelectedSortimentStore(this)

        SumUpState.init(this)

        // Das SumUp-Reader-SDK haelt eine eigene, persistente Merchant-Session
        // auf dem Geraet. Ohne Logout beim Unpair wuerde ein spaeter neu
        // gekoppelter Verein weiter auf den alten Merchant kassieren.
        deviceSessionRepository.addOnUnpairedListener {
            sumupTokenRepository.invalidate()
            try {
                com.sumup.merchant.reader.api.SumUpAPI.logout()
            } catch (e: Exception) {
                Log.w("KassierApplication", "SumUp logout on unpair failed", e)
            }
        }

        setupKioskMode()
    }

    // The Firebase App Check debug provider stores its secret in
    // SharedPreferences ("com.google.firebase.appcheck.debug.store.<persistenceKey>",
    // key "com.google.firebase.appcheck.debug.DEBUG_SECRET") and generates a
    // random UUID there if none is set yet — which changes on every reinstall
    // and would need re-registering in the Firebase Console each time. Pinning
    // it here keeps it stable across builds/reinstalls of debug test tablets.
    private fun pinDebugAppCheckSecret(firebaseApp: FirebaseApp) {
        getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}",
            Context.MODE_PRIVATE
        ).edit()
            .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", Config.APP_CHECK_DEBUG_TOKEN)
            .apply()
    }

    private fun setupKioskMode() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, KioskAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(
                packageName,
                "com.android.settings"
            ))
        }
    }
}
