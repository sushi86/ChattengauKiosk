package net.maerkl.kassierapp

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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
import kotlinx.serialization.json.Json
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.local.EncryptedDeviceSessionStore
import net.maerkl.kassierapp.data.local.EncryptedSelectedSortimentStore
import net.maerkl.kassierapp.data.local.SelectedSortimentStore
import net.maerkl.kassierapp.data.preferences.SettingsDataStore
import net.maerkl.kassierapp.data.remote.AppCheckTokenProvider
import net.maerkl.kassierapp.data.remote.ArtikelRepository
import net.maerkl.kassierapp.data.remote.BackendApi
import net.maerkl.kassierapp.data.remote.FirebasePairingService
import net.maerkl.kassierapp.data.remote.SortimentRepository
import net.maerkl.kassierapp.data.remote.TransaktionRepository
import net.maerkl.kassierapp.data.repository.AuthModeResolver
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
    lateinit var authModeResolver: AuthModeResolver
        private set
    lateinit var transaktionRepository: TransaktionRepository
        private set
    lateinit var artikelRepository: ArtikelRepository
        private set
    lateinit var sortimentRepository: SortimentRepository
        private set
    lateinit var selectedSortimentStore: SelectedSortimentStore
        private set

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        val providerFactory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(providerFactory)

        val auth = FirebaseAuth.getInstance()
        val functions = FirebaseFunctions.getInstance(Config.FIREBASE_FUNCTIONS_REGION)

        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val backendApi = BackendApi(httpClient, Config.BACKEND_BASE_URL)
        val appCheckProvider = AppCheckTokenProvider(appCheck)
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
        artikelRepository = ArtikelRepository(firestore)
        sortimentRepository = SortimentRepository(firestore)
        selectedSortimentStore = EncryptedSelectedSortimentStore(this)
        authModeResolver = AuthModeResolver(
            pairingStateFlow = deviceSessionRepository.pairingState,
            manualAffiliateKeyFlow = settingsDataStore.affiliateKey,
            manualOauthTokenFlow = settingsDataStore.oauthToken
        )

        SumUpState.init(this)
        setupKioskMode()
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
